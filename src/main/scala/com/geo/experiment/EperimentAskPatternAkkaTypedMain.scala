package com.geo.experiment

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout

import scala.util.{Failure, Success}

/**
 * @author Gemuruh Geo Pratama
 * @created 20/07/2021-06.06
 */
object EperimentAskPatternAkkaTypedMain extends App {

  object CookieFabric {
    trait Request

    case class GiveMeACookie(replayTo: ActorRef[Response]) extends Request

    trait Response

    case class HereIsYourCookie(number: Int, desc: String) extends Response

    def apply(): Behavior[Request] = Behaviors.receiveMessage {
      case GiveMeACookie(replayTo) =>
        replayTo ! HereIsYourCookie(5, "Cookie Manis")
        Behaviors.same
    }
  }

  import CookieFabric._

  val rootBehaviour: Behavior[Any] = Behaviors.setup {
    context =>
      implicit val scheduler = context.system.scheduler
      implicit val timeout: Timeout = 3.seconds
      implicit val ec = context.executionContext
      val cookieActor = context.spawn(CookieFabric(), "CookieActor")
      val responseF = cookieActor.ask(GiveMeACookie)

      responseF.onComplete {
        case Success(value) =>
          value match {
            case HereIsYourCookie(number, desc) =>
              println(s"Ini cookie mu, jumlahnya $number rasanya $desc")
          }
        case Failure(exception) => println(exception)
      }

      Behaviors.empty
  }
  val actorSystem = ActorSystem(rootBehaviour, "ActorSystem")


}
