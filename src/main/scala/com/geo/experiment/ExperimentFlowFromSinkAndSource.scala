package com.geo.experiment

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.typed.scaladsl.ActorSink

/**
 * @author Gemuruh Geo Pratama
 * @created 21/07/2021-08.42
 */
object ExperimentFlowFromSinkAndSource extends App {
  object SinkActorInstance {
    sealed trait Request

    case class Message(msg: String, replayTo: ActorRef[Response]) extends Request

    case object Disconnected extends Request

    sealed trait Response

    case class ResponseMessage(rMsg: String) extends Response

    def apply(): Behavior[Request] = Behaviors.setup {
      context =>
        val log = context.log
        Behaviors.receiveMessage {
          case Message(msg, replayTo) =>
            log.info("Receive Mesage := {}", msg)
            replayTo ! ResponseMessage(s"ECHO $msg")
            Behaviors.same

          case Disconnected =>
            Behaviors.stopped
        }
    }
  }

  val rootActor: Behavior[Any] = Behaviors.setup {
    context =>
      val sinkActorInstance = context.spawn(SinkActorInstance(), "SinkActor")
      val sinkActor = ActorSink.actorRef[SinkActorInstance.Request](
        ref = sinkActorInstance,
        onCompleteMessage = SinkActorInstance.Disconnected,
        onFailureMessage = _ => SinkActorInstance.Disconnected)


      Behaviors.empty
  }
}
