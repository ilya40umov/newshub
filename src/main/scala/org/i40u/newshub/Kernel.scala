package org.i40u.newshub

import java.io.File

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContext

/**
 * @author ilya40umov
 */
trait Kernel {

  def home: File

  def config: Config

  implicit def system: ActorSystem

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
