/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.zeromq

import akka.actor.{Actor, ActorRef}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Duration
import akka.util.duration._
import java.util.Arrays
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers

class ConcurrentSocketActorSpec extends WordSpec with MustMatchers with TestKit {
  val endpoint = "inproc://PubSubConnectionSpec"
  "ConcurrentSocketActor" should {
    "support pub-sub connections" in {
      val (publisherProbe, subscriberProbe) = (TestProbe(), TestProbe())
      val message = ZMQMessage("hello".getBytes)
      var context: Option[Context] = None
      var publisher: Option[ActorRef] = None
      var subscriber: Option[ActorRef] = None
      try {
        context = Some(ZeroMQ.newContext)
        publisher = newPublisher(context.get, publisherProbe.ref)
        subscriber = newSubscriber(context.get, subscriberProbe.ref)
        subscriberProbe.within(5 seconds) {
          subscriberProbe.expectMsg(Connecting)
          publisher ! message
          subscriberProbe.expectMsg(message)
        }
      } finally {
        subscriber.foreach(_.stop)
        publisher.foreach(_.stop)
        subscriberProbe.within(5 seconds) {
          subscriberProbe.expectMsg(Closed)
        }
        context.foreach(_.term)
      }
    }
    "support zero-length message frames" in {
      val publisherProbe = TestProbe()
      var publisher: Option[ActorRef] = None
      var context: Option[Context] = None
      try {
        context = Some(ZeroMQ.newContext)
        publisher = newPublisher(context.get, publisherProbe.ref)
        publisher ! ZMQMessage(Seq[Frame]())
      } finally {
        publisher.foreach(_.stop)
        publisherProbe.within(5 seconds) {
          publisherProbe.expectMsg(Closed)
        }
        context.foreach(_.term)
      }
    }
    def newPublisher(context: Context, listener: ActorRef) = {
      val publisher = ZeroMQ.newSocket(SocketParameters(context, SocketType.Pub, Some(listener)))
      publisher ! Bind(endpoint)
      Some(publisher)
    }
    def newSubscriber(context: Context, listener: ActorRef) = {
      val subscriber = ZeroMQ.newSocket(SocketParameters(context, SocketType.Sub, Some(listener)))
      subscriber ! Connect(endpoint)
      subscriber ! Subscribe(Seq())
      Some(subscriber)
    }
  }
}
