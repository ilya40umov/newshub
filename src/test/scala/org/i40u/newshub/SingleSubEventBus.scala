package org.i40u.newshub

import akka.actor.ActorRef
import akka.event.EventBus

/**
 * @author ilya40umov
 */
class SingleSubEventBus(sub: ActorRef) extends EventBus {

  type Event = AnyRef
  type Classifier = Class[_]
  type Subscriber = ActorRef

  override def subscribe(subscriber: ActorRef, to: Class[_]): Boolean = throw new UnsupportedOperationException

  override def publish(event: AnyRef): Unit = sub ! event

  override def unsubscribe(subscriber: ActorRef, from: Class[_]): Boolean = throw new UnsupportedOperationException

  override def unsubscribe(subscriber: ActorRef): Unit = throw new UnsupportedOperationException
}
