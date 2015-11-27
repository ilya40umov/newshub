package org.i40u.newshub

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.{Config, ConfigFactory}
import org.i40u.newshub.Kernel.EventBus

import scala.concurrent.ExecutionContext

/**
 * @author ilya40umov
 */
object Kernel {
  type EventBus = akka.event.EventBus {type Event = AnyRef; type Classifier = Class[_]; type Subscriber = ActorRef}
}

trait Kernel {

  def home: File

  def config: Config

  implicit def system: ActorSystem

  def eventBus: EventBus = system.eventStream

  implicit def executionContext: ExecutionContext
}

trait DefaultKernel extends Kernel {
  override val home: File = {
    Option(System.getProperty("user.dir")) match {
      case Some(appHome) => new File(appHome)
      case None => throw new Error("{user.dir} property can't be found!")
    }
  }
  override lazy val config = ConfigFactory.load()
  override implicit lazy val system = ActorSystem("news-hub", config)
  override implicit lazy val executionContext = system.dispatcher
  sys.addShutdownHook(system.shutdown())
}
