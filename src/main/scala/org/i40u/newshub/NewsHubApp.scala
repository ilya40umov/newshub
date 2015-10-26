package org.i40u.newshub

import akka.actor.{Actor, Props}
import akka.pattern.pipe
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.crawler.DefaultCrawler
import org.i40u.newshub.http.DefaultHttpAccess
import org.i40u.newshub.storage.{DefaultStorage, Feed}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * @author ilya40umov
 */
object NewsHubApp
  extends App
  with DefaultKernel with DefaultStorage with DefaultHttpAccess with DefaultCrawler {

  Await.ready(feedRepository.save(Feed("http://winteriscoming.net/feed/", "Winter is Coming")), 1.minute)
  Await.ready(feedRepository.flushIndex(), 1.minute)

  kickOffCrawler()

  system.actorOf(Props(classOf[MonitoringActor]), "monitor")

  class MonitoringActor extends Actor with StrictLogging {

    context.system.scheduler.schedule(1.seconds, 10.seconds, self, "check-in")

    override def receive: Receive = {
      case "check-in" =>
        val counts = for {
          aboutAnything <- articleRepository.performSearch("", 0, 100).map(_.size)
          aboutJon <- articleRepository.performSearch("Jon Snow", 0, 100).map(_.size)
        } yield (aboutAnything, aboutJon)
        counts pipeTo self
      case (aboutAnything: Int, aboutJon: Int) =>
        logger.info(s"About anything: $aboutAnything, about Jon Snow: $aboutJon")
    }
  }

}