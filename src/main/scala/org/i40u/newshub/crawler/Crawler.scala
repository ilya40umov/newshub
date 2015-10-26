package org.i40u.newshub.crawler

import akka.actor.ActorRef
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.Kernel
import org.i40u.newshub.crawler.PollingManager.KickOffPolling
import org.i40u.newshub.http.HttpAccess
import org.i40u.newshub.storage.Storage

/**
 * @author ilya40umov
 */
trait Crawler {

  def kickOffCrawler(): Unit

}

trait DefaultCrawler extends Crawler with StrictLogging {
  self: Kernel with Storage with HttpAccess =>

  override def kickOffCrawler(): Unit = {
    logger.info("Starting up Article Crawler.")
    pollingManager ! KickOffPolling
  }

  lazy val feedParser: FeedParser = new FeedParserImpl

  lazy val contentExtractor: ContentExtractor = new GooseContentExtractor

  lazy val scrapingManager: ActorRef = system.actorOf(ScrapingManager.props(config, articleRepository,
    httpAccessManager, contentExtractor), "scrapingManager")

  lazy val pollingManager: ActorRef = system.actorOf(PollingManager.props(config, feedRepository, articleRepository,
    httpAccessManager, feedParser, scrapingManager), "pollingManager")

}
