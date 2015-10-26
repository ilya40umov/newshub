package org.i40u.newshub.crawler

import akka.actor.{Actor, ActorRef, Props, Status}
import akka.pattern.pipe
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.crawler.FeedParser.FeedItem
import org.i40u.newshub.crawler.ScrapingManager.{GimmeItemToScrape, ScrapeFeedItem}
import org.i40u.newshub.http.HttpAccessManager.{Fetch, FetchResult}
import org.i40u.newshub.http.HttpResponse
import org.i40u.newshub.storage.{Article, ArticleRepository}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * @author ilya40umov
 */
object ScrapingWorker {

  def props(httpAccessManager: ActorRef,
            contentExtractor: ContentExtractor,
            articleRepository: ArticleRepository) =
    Props(classOf[ScrapingWorker], httpAccessManager, contentExtractor, articleRepository)

  class ScrapingException(message: String = null, cause: Throwable = null)
    extends RuntimeException(message, cause)

}

class ScrapingWorker(httpAccessManager: ActorRef,
                     contentExtractor: ContentExtractor,
                     articleRepository: ArticleRepository)
  extends Actor with StrictLogging {

  import ScrapingWorker._
  import context.dispatcher

  context.parent ! GimmeItemToScrape

  override def receive: Receive = receiveScrapeFeedItem

  def receiveScrapeFeedItem: Receive = {
    case ScrapeFeedItem(feedItem) =>
      articleRepository.existsForUrl(feedItem.url) pipeTo self
      context.become(receiveArticleExists(feedItem))
  }

  def receiveArticleExists(feedItem: FeedItem): Receive = {
    case true =>
      // already exists => we can safely ignore it
      context.parent ! GimmeItemToScrape
      context.become(receiveScrapeFeedItem)
    case false | Status.Failure(_) =>
      logger.debug("Starting scrapping: {}", feedItem.url)
      httpAccessManager ! Fetch(feedItem.url)
      context.become(receiveFetchResult(feedItem))
  }

  def receiveFetchResult(feedItem: FeedItem): Receive = {
    case fetchResult: FetchResult =>
      val futureSavedArticle = fetchResult match {
        case FetchResult(_, Success(HttpResponse(status, articleBody, finalUrl))) =>
          if (status == 200) {
            Future.fromTry(contentExtractor.extractText(finalUrl, articleBody)) flatMap { extractedContent =>
              articleRepository.upsert(finalUrl) {
                case Some(article) =>
                  // article already indexed, just adding this [origUrl -> url] pair
                  article.copy(
                    origUrls = article.origUrls + feedItem.url,
                    imageUrl = article.imageUrl orElse feedItem.imageUrl
                  )
                case None =>
                  Article(url = finalUrl, origUrls = Set(feedItem.url), title = feedItem.title,
                    content = extractedContent, pubDate = feedItem.pubDate, imageUrl = feedItem.imageUrl)
              }
            }
          } else {
            Future.failed(new ScrapingException(s"Got non-OK status code: $status for feed item: ${feedItem.url}"))
          }
        case FetchResult(_, Failure(cause)) =>
          Future.failed(new ScrapingException(s"Failed to fetch feed item: ${feedItem.url}", cause))
      }
      futureSavedArticle pipeTo self
      context.become(receiveSavedArticle(feedItem))
  }

  def receiveSavedArticle(feedItem: FeedItem): Receive = {
    case savedArticle: Article =>
      logger.debug("Article is successfully scraped and saved to the repository! {}", savedArticle)
      context.parent ! GimmeItemToScrape
      context.become(receiveScrapeFeedItem)
    case Status.Failure(cause) =>
      logger.warn("Couldn't scrape a feed item! {}", feedItem.url, cause)
      context.parent ! GimmeItemToScrape
      context.become(receiveScrapeFeedItem)
  }

}
