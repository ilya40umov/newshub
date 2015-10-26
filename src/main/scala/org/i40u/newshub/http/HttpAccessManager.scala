package org.i40u.newshub.http

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.mutable
import scala.util.Try

/**
 * @author ilya40umov
 */
object HttpAccessManager {

  def props(config: Config, httpClient: HttpClient) = Props(classOf[HttpAccessManager], config, httpClient)

  case class Fetch(url: String)

  case class FetchResult(fetch: Fetch, result: Try[HttpResponse])

  private[http] case class FetchJob(fetch: Fetch,
                                    replyTo: ActorRef,
                                    triedProxyIds: Set[String]) {
    val jobId: String = fetch.url + "-" + replyTo.toString()
  }

  private[http] case class GimmeJob(workerProxyId: String)

}

class HttpAccessManager(config: Config, httpClient: HttpClient) extends Actor with StrictLogging {

  import org.i40u.newshub.http.HttpAccessManager._

  val maxConPerProxy = config.getInt("http.max-con-per-proxy")
  val proxies = config.getStringList("http.proxies").asScala.map(HttpProxy(_))

  logger.info(s"Detected ${proxies.size} proxies.")

  proxies.flatMap(proxy => Seq.fill(maxConPerProxy)(proxy)).zipWithIndex.foreach { case (proxy, idx) =>
    context.actorOf(HttpAccessWorker.props(proxy, config, httpClient), s"http-worker-$idx")
  }

  val queuedJobsById = mutable.LinkedHashMap.empty[String, FetchJob]
  val idleWorkersToProxy = mutable.HashMap.empty[ActorRef, String]

  override def receive: Receive = {
    case fetch@Fetch(url) =>
      enqueueJob(FetchJob(fetch, sender(), Set.empty))
    case fetchJob: FetchJob =>
      enqueueJob(fetchJob)
    case GimmeJob(proxyId) =>
      dequeueJob(proxyId) match {
        case Some(fetchJob) => sender() ! fetchJob
        case None => idleWorkersToProxy += sender() -> proxyId
      }
  }

  def dequeueJob(forProxyId: String): Option[FetchJob] = {
    val foundJob = queuedJobsById.values.find { job => !job.triedProxyIds.contains(forProxyId) }
    foundJob.foreach { job => queuedJobsById -= job.jobId }
    foundJob
  }

  def enqueueJob(fetchJob: FetchJob): Unit = {
    idleWorkersToProxy.find { case (worker, proxyId) =>
      !fetchJob.triedProxyIds.contains(proxyId)
    } match {
      case Some((worker, _)) =>
        idleWorkersToProxy -= worker
        worker ! fetchJob
      case None =>
        queuedJobsById += fetchJob.jobId -> fetchJob
    }
  }
}
