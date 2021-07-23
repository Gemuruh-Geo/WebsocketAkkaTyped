package com.geo

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.geo.WebSocketExperiment.EventHandler.Join
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * @author Gemuruh Geo Pratama
 * @created 20/07/2021-15.17
 */
object WebSocketExperiment extends App {
  object UserActor {
    trait Event
    case class IncomingMessage(message: String) extends Event
    case class Register(phoneNumber: String) extends Event
    case class OutgoingMessage(message: String) extends Event
    case class Init(outgoingActorRef: ActorRef[OutgoingMessage]) extends Event
    case object Completed extends Event
    case class Failure(ex: Throwable) extends Event

    def apply(eventHandlerRef: ActorRef[EventHandler.Handler]): Behavior[Event] = handleConnection(eventHandlerRef)

    def handleConnection(eventHandlerRef: ActorRef[EventHandler.Handler]): Behavior[Event] = Behaviors.setup {
      context =>
        val log = context.log
        Behaviors.receiveMessage {
          case Init(outgoing) =>
            log.info("Start Init")
            connect(eventHandlerRef, outgoing)

          case Completed =>
            Behaviors.stopped
          case Failure(ex) =>
            log.error("e", ex)
            Behaviors.stopped

        }
    }
    def connect(evenHandlerRef: ActorRef[EventHandler.Handler], outgoingActorRef: ActorRef[OutgoingMessage]): Behavior[Event] = Behaviors.setup {
      context =>
        val log = context.log
        val adapter = context.messageAdapter[EventHandler.Handler] {
          case EventHandler.MessagePush(messageFormat) => OutgoingMessage(messageFormat)
          case EventHandler.Join(phoneNumber, _) => OutgoingMessage(s"Phone Number $phoneNumber is connected Ready To Accept Message")
        }
        Behaviors.receiveMessage {
          case Register(phoneNumber) =>
            log.info("Start To Register with phone number {}", phoneNumber)
            evenHandlerRef ! Join(phoneNumber, adapter)
            Behaviors.same

          case IncomingMessage(message) =>
            log.info("Incoming ...")
            evenHandlerRef ! EventHandler.MessagePush(message)
            Behaviors.same

          case out: OutgoingMessage =>
            log.info("Out Going")
            outgoingActorRef ! out
            Behaviors.same
        }
    }

  }
  object EventHandler {
    trait Handler
    case class Join(phoneNumber: String, handlerActorRef: ActorRef[Handler]) extends Handler
    case class MessagePush(messageFormat: String) extends Handler

    def doHandleBehaviour(map: Map[String, ActorRef[Handler]]): Behavior[Handler] = Behaviors.setup {
      context =>
        val log = context.log
        Behaviors.receiveMessage {
          case Join(phoneNumber, refHandler) =>
            log.info("phone number {} Start To Join ...", phoneNumber)
            doHandleBehaviour(map + (phoneNumber -> refHandler))
          case MessagePush(messageFormat) =>
            println(s"********* $messageFormat")
            if(messageFormat == "ping") {
              Behaviors.same
            }else {
              val datas = messageFormat.split("=")
              val phoneNumber = datas(0)
              val realMessage = datas(1)
              val handlerRef = map(phoneNumber)
              //This Message Push Will be handle in adapter User Actor
              handlerRef ! MessagePush(realMessage)
              Behaviors.same
            }
        }

    }
    def apply(): Behavior[Handler] = doHandleBehaviour(Map())
  }

  //Server Logic
  val rootBehaviour: Behavior[Any] = Behaviors.setup {
    context =>
      implicit val applicationSystem: ActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContext = context.executionContext
      val eventHandlerActor = context.spawn(EventHandler(),"EventHandlerActor")
      val userActor = context.spawn(UserActor(eventHandlerActor), "UserActor")
      val incomingMessages: Sink[Message, NotUsed] = Flow[Message]
        .map {
          case TextMessage.Strict(text) =>
            val datas = text.split("=")
            val requestType = datas(0)
            if(requestType=="Register") {
              val phoneNumber = datas(1)
              UserActor.Register(phoneNumber)
            }else {
              UserActor.IncomingMessage(text)
            }
          }.to(ActorSink.actorRef(ref = userActor, onCompleteMessage = UserActor.Completed, onFailureMessage = UserActor.Failure))

      val outgoingMessages: Source[Message, NotUsed] = ActorSource
        .actorRef[UserActor.OutgoingMessage](completionMatcher = PartialFunction.empty, failureMatcher = PartialFunction.empty, bufferSize = 10, overflowStrategy = OverflowStrategy.fail)
        .mapMaterializedValue( outActorRef => {
          //We need this mapMaterializedValue to init
          userActor ! UserActor.Init(outActorRef)
          NotUsed
        }).map { case UserActor.OutgoingMessage(message) => TextMessage(message)}

      val flowWS: Flow[Message, Message, NotUsed] = Flow.fromSinkAndSourceCoupled(incomingMessages, outgoingMessages)

      //Route Logic, Including websocket route
      val route: Route = concat(
        path("ws") {
          get {
            handleWebSocketMessages(flowWS)
          }
        },
        path("push") {
          get {
            userActor ! UserActor.IncomingMessage("081226408778=Hahahah Iam from Istanbul")
            complete("ok")
          }
        }
      )

      Http().newServerAt("localhost",8080).bind(route).onComplete {
        case Success(binding) =>
          println(
            s"Started server at ${binding.localAddress.getHostString}:${binding.localAddress.getPort}"
          )
        case Failure(ex) =>
          ex.printStackTrace()
          println("Server failed to start, terminating")
          context.system.terminate()
      }
      Behaviors.empty

  }

  ActorSystem(rootBehaviour, "MySystem")
}
