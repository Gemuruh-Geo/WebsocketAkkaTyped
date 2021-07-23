package com.geo.experiment

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

/**
 * @author Gemuruh Geo Pratama
 * @created 19/07/2021-15.04
 */
object ExprimentAdapterPatternMain extends App {
  object StoreDomain {
    case class Product(name: String, price: Double)
  }

  object ShoppingCart {

    import StoreDomain._

    val db: Map[String, List[Product]] = Map(
      "123-abc-456" -> List(Product("iPhone", 7000), Product("selfie stick", 30))
    )

    sealed trait Request

    case class GetCurrentChart(cartId: String, replayTo: ActorRef[Response]) extends Request

    sealed trait Response

    case class CurrentChart(chartId: String, items: List[Product]) extends Response

    def apply(): Behavior[Request] = Behaviors.setup {
      context =>
        Behaviors.receiveMessage {
          case GetCurrentChart(cartId, replayTo) =>
            replayTo ! CurrentChart(cartId, db(cartId))
            Behaviors.same
        }
    }
  }

  object Checkout {

    import ShoppingCart._

    sealed trait Request

    final case class InspectSummary(cartId: String, replayTo: ActorRef[Response]) extends Request

    sealed trait Response

    final case class Summary(cartId: String, totalPrice: Double) extends Response

    private case class WrapperResponseShoppingCart(response: ShoppingCart.Response) extends Request

    def apply(shoppingCartRef: ActorRef[ShoppingCart.Request]): Behavior[Request] = Behaviors.setup {
      context =>
        val responseMapper: ActorRef[ShoppingCart.Response] = context.messageAdapter(rsp => WrapperResponseShoppingCart(rsp))

        def handleMessage(map: Map[String, ActorRef[Response]]): Behavior[Request] = Behaviors.receiveMessage {
          case InspectSummary(cartId, replayTo) =>
            shoppingCartRef ! ShoppingCart.GetCurrentChart(cartId = cartId, responseMapper)
            //Di buffer dulu actor replay, karena kita tidak bisa langsung kirim replay
            //Message baru datang ketika di handle oleh WrapperResponseShoppingCart
            handleMessage(map + (cartId -> replayTo))

          case WrapperResponseShoppingCart(response) =>
            response match {
              case CurrentChart(cartId, items) =>
                val totalPrice = items.map(_.price).sum
                val summary = Summary(cartId = cartId, totalPrice)
                map(cartId) ! summary
                Behaviors.same
            }
        }

        handleMessage(Map())

    }

  }

  val rootBehaviour: Behavior[Any] = Behaviors.setup {
    context =>
      val shoppingCartActor = context.spawn(ShoppingCart(), "ShoppingCartActor")
      val checkoutActor = context.spawn(Checkout(shoppingCartActor), "CheckoutActor")

      val testingBehaviour: Behavior[Checkout.Response] = Behaviors.receiveMessage {
        case Checkout.Summary(cartId, totalPrice) =>
          println(s"Chart Id = $cartId and total price = $totalPrice")
          Behaviors.same
      }
      val testingActor = context.spawn(testingBehaviour, "TestingBehaviour")
      checkoutActor ! InspectSummary("123-abc-456", testingActor)
      Behaviors.empty
  }

  val actorSystem = ActorSystem(rootBehaviour, "Application")

}
