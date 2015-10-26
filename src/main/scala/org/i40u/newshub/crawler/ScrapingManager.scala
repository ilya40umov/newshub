package org.i40u.newshub.crawler

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.crawler.FeedParser.FeedItem
import org.i40u.newshub.storage.ArticleRepository

import scala.collection.mutable

/**
 * @author ilya40umov
 */
object ScrapingManager {

  def props(config: Config,
            articleRepository: ArticleRepository,
            httpAccessManager: ActorRef,
            contentExtractor: ContentExtractor) =
    Props(classOf[ScrapingManager], config, articleRepository, httpAccessManager, contentExtractor)

  case class ScrapeFeedItems(feedItems: Seq[FeedItem])

  case class ScrapeFeedItem(feedItem: FeedItem)

  case object GimmeItemToScrape

}

class ScrapingManager(config: Config,
                      articleRepository: ArticleRepository,
                      httpAccessManager: ActorRef,
                      contentExtractor: ContentExtractor) extends Actor with StrictLogging {

  import ScrapingManager._

  val idleWorkers = mutable.Queue.empty[ActorRef]
  val queuedItems = mutable.LinkedHashSet.empty[FeedItem]
  val queuedItemUrls = mutable.HashSet.empty[String]

  1 to config.getInt("scaping.num-of-workers") foreach { idx =>
    context.actorOf(ScrapingWorker.props(httpAccessManager, contentExtractor, articleRepository),
      s"scrapingWorker-$idx")
  }

  override def receive: Receive = receiveScrapeFeedItems orElse receiveGimmeItemToScrape

  def receiveScrapeFeedItems: Receive = {
    case ScrapeFeedItems(feedItems) =>
      feedItems.foreach(item => enqueueItem(item))
  }

  def receiveGimmeItemToScrape: Receive = {
    case GimmeItemToScrape =>
      dequeueItem() match {
        case Some(feedItem) =>
          sender() ! ScrapeFeedItem(feedItem)
        case None =>
          idleWorkers += sender()
      }
  }

  def enqueueItem(feedItem: FeedItem): Unit = {
    if (!queuedItemUrls.contains(feedItem.url)) {
      logger.debug("Accepted a new item to be fetched and scraped: {}", feedItem)
      if (idleWorkers.nonEmpty) {
        idleWorkers.dequeue() ! ScrapeFeedItem(feedItem)
      } else {
        queuedItems += feedItem
        queuedItemUrls += feedItem.url
      }
    } else {
      logger.debug("Ignored an item already queued for fetching/scraping: {}", feedItem)
    }
  }

  def dequeueItem(): Option[FeedItem] = {
    val feedItem = queuedItems.headOption
    feedItem.foreach { item =>
      queuedItems -=  item
      queuedItemUrls -= item.url
    }
    feedItem
  }

}