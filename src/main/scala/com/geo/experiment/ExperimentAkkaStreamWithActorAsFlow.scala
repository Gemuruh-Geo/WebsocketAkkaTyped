package com.geo.experiment

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.Timeout

/**
 * @author Gemuruh Geo Pratama
 * @created 20/07/2021-09.16
 */
object ExperimentAkkaStreamWithActorAsFlow extends App {
  trait Request

  case class RequestToDuplicateNumber(n: Int, replayTo: ActorRef[Response]) extends Request

  trait Response

  case class ResponseDuplicate(duplicatedNumber: Int) extends Response

  object Simple {

    def handleBehaviour(): Behavior[Request] = Behaviors.setup {
      context =>
        implicit val ce = context.executionContext
        val log = context.log
        Behaviors.receiveMessage {
          case RequestToDuplicateNumber(n, replayTo) =>
            log.info(s"Just Receive a number $n")
            val dup = 2 * n
            replayTo ! ResponseDuplicate(dup)
            Behaviors.same

        }
    }

    def apply(): Behavior[Request] = handleBehaviour()
  }

  val rootBehaviour: Behavior[Any] = Behaviors.setup {
    context =>
      val log = context.log
      val simpleActor = context.spawn(Simple(), "SimpleActor")
      val numberSource = Source(1 to 10)
      implicit val timeout: Timeout = 120.seconds
      implicit val system = context.system
      val actorBasedFlow: Flow[Int, Response, NotUsed] = ActorFlow.ask(simpleActor) {
        (i, rep) =>
          RequestToDuplicateNumber(i, rep)
      }
      numberSource.via(actorBasedFlow).to(Sink.ignore).run()
      Behaviors.empty
  }
  val actorSystem = ActorSystem(rootBehaviour, "TestingSystem")

}
