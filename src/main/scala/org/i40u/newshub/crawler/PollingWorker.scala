package org.i40u.newshub.crawler

import akka.actor.{Status, Actor, ActorRef, Props}
import akka.pattern.pipe
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.crawler.FeedParser.ParsedFeed
import org.i40u.newshub.crawler.PollingManager.{GimmeUrlToPoll, PollFeed}
import org.i40u.newshub.crawler.ScrapingManager.ScrapeFeedItems
import org.i40u.newshub.http.HttpAccessManager.{Fetch, FetchResult}
import org.i40u.newshub.http.HttpResponse
import org.i40u.newshub.storage.ArticleRepository

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * @author ilya40umov
 */
object PollingWorker {
  def props(httpAccessManager: ActorRef,
            feedParser: FeedParser,
            articleRepository: ArticleRepository,
            scrapingManager: ActorRef) =
    Props(classOf[PollingWorker], httpAccessManager, feedParser, articleRepository, scrapingManager)

  class PollingException(message: String = null, cause: Throwable = null)
    extends RuntimeException(message, cause)
}

class PollingWorker(httpAccessManager: ActorRef,
                    feedParser: FeedParser,
                    articleRepository: ArticleRepository,
                    scrapingManager: ActorRef)
  extends Actor with StrictLogging {

  import PollingWorker._
  import context.dispatcher

  context.parent ! GimmeUrlToPoll

  override def receive: Receive = receivePollFeed

  def receivePollFeed: Receive = {
    case PollFeed(url) =>
      logger.debug("Polling feed: {}", url)
      httpAccessManager ! Fetch(url)
      context.become(receiveFetchResult(url))
  }

  def receiveFetchResult(feedUrl: String): Receive = {
    case fetchResult: FetchResult =>
      val futureFeedItems = fetchResult match {
        case FetchResult(_, Success(HttpResponse(status, feedBody, _))) =>
          if (status == 200) {
            feedParser.parse(feedUrl, feedBody) match {
              case Success(ParsedFeed(_, feedItems)) =>
                Future.fold {
                  feedItems.map { item =>
                    articleRepository.existsForUrl(item.url).map(exists => item -> exists)
                  }
                } {
                  List.empty[FeedParser.FeedItem]
                } { case (newItems, (item, exists)) =>
                  if (exists) newItems else item :: newItems
                }
              case Failure(exception) =>
                Future.failed(new PollingException(s"Failed to parse feed: $feedUrl\n\n\n$feedBody\n\n\n", exception))
            }
          } else {
            Future.failed(new PollingException(s"Got non-OK status code: $status for feed: $feedUrl"))
          }
        case FetchResult(_, Failure(cause)) =>
          Future.failed(new PollingException(s"Failed to fetch feed: $feedUrl", cause))
      }
      futureFeedItems pipeTo self
      context.become(receiveFoundFeedItems(feedUrl))
  }

  def receiveFoundFeedItems(feedUrl: String): Receive = {
    case foundFeedItems: List[FeedParser.FeedItem @unchecked] =>
      if (foundFeedItems.nonEmpty) {
        scrapingManager ! ScrapeFeedItems(foundFeedItems)
      }
      context.parent ! GimmeUrlToPoll
      context.become(receivePollFeed)
    case Status.Failure(cause) =>
      logger.warn("Failed to poll a feed!", cause)
      context.parent ! GimmeUrlToPoll
      context.become(receivePollFeed)
  }

}
