package org.i40u.newshub.crawler

import akka.actor._
import akka.pattern._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.storage.{ArticleRepository, Feed, FeedRepository}

import scala.collection.mutable
import scala.concurrent.duration._

/**
 * @author ilya40umov
 */
object PollingManager {

  def props(config: Config,
            feedRepository: FeedRepository,
            articleRepository: ArticleRepository,
            httpAccessManager: ActorRef,
            feedParser: FeedParser,
            scrapingManager: ActorRef) =
    Props(classOf[PollingManager], config, feedRepository, articleRepository, httpAccessManager, feedParser,
      scrapingManager)

  case object KickOffPolling

  case class PollFeed(url: String)

  case object GimmeUrlToPoll

}

class PollingManager(config: Config,
                     feedRepository: FeedRepository,
                     articleRepository: ArticleRepository,
                     httpAccessManager: ActorRef,
                     feedParser: FeedParser,
                     scrapingManager: ActorRef)
  extends Actor with StrictLogging {

  import PollingManager._
  import context.dispatcher

  val scheduler = context.system.scheduler
  val idleWorkers = mutable.Queue.empty[ActorRef]
  val queuedFeeds = mutable.LinkedHashSet.empty[PollFeed]

  1 to config.getInt("polling.num-of-workers") foreach { idx =>
    context.actorOf(PollingWorker.props(httpAccessManager, feedParser, articleRepository, scrapingManager),
      s"pollingWorker-$idx")
  }

  override def receive: Receive = receiveKickOff

  def receiveKickOff: Receive = receiveGimmeUrlToPoll orElse {
    case KickOffPolling =>
      logger.info("Searching for feeds to start polling on.")
      findAllFeeds pipeTo self
      context.become(receiveFeeds)
  }
  
  def receiveFeeds: Receive = receiveGimmeUrlToPoll orElse {
    case feeds: Seq[Feed@unchecked] =>
      logger.info(s"Found ${feeds.size} feeds. Scheduling them for polling.")
      feeds.foreach { feed =>
        // XXX in real life we should randomize to smooth up the schedule
        // TODO use Random.nextInt(15).minutes for the initial delay
        scheduler.schedule(0.minutes, 15.minutes, self, PollFeed(feed.url))
      }
      context.become(receivePollFeed)
    case Status.Failure(cause) =>
      logger.error("Failed to load the feeds from repository. Retrying...", cause)
      findAllFeeds pipeTo self
  }

  def receivePollFeed: Receive = receiveGimmeUrlToPoll orElse {
    case pollFeed: PollFeed =>
      enqueuePoll(pollFeed)
  }

  def receiveGimmeUrlToPoll: Receive = {
    case GimmeUrlToPoll =>
      dequeuePoll() match {
        case Some(pollFeed) =>
          sender() ! pollFeed
        case None =>
          idleWorkers += sender()
      }
  }

  def findAllFeeds = feedRepository.find(title = None, from = 0, limit = 50000)

  def enqueuePoll(pollFeed: PollFeed): Unit = {
    if (idleWorkers.nonEmpty) {
      idleWorkers.dequeue() ! pollFeed
    } else {
      queuedFeeds += pollFeed
    }
  }

  def dequeuePoll(): Option[PollFeed] = {
    val pollFeed = queuedFeeds.headOption
    pollFeed.foreach(queuedFeeds -= _)
    pollFeed
  }
}
