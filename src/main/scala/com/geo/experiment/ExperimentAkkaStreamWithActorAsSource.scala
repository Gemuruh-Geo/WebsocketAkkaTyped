package com.geo.experiment

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl.ActorSource

/**
 * @author Gemuruh Geo Pratama
 * @created 20/07/2021-11.03
 */
object ExperimentAkkaStreamWithActorAsSource extends App {
  implicit val system = ActorSystem(Behaviors.empty, "MySystem")

  trait Request

  case class Message(msg: String) extends Request

  case object Complete extends Request

  case class Fail(ex: Exception) extends Request

  val source: Source[Request, ActorRef[Request]] = ActorSource.actorRef[Request](bufferSize = 8, overflowStrategy = OverflowStrategy.fail, completionMatcher = {
    case Complete =>
  }, failureMatcher = {
    case Fail(ex) => ex
  })

  val actorRef = source.collect {
    case Message(msg) => msg
  }.to(Sink.foreach(println)).run()

  actorRef ! Message("Hello")
  actorRef ! Message("Hello")
}
