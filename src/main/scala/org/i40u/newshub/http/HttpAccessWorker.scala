package org.i40u.newshub.http

import akka.actor.{Actor, Props, Status}
import akka.pattern._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.http.HttpAccessManager.{Fetch, FetchJob, FetchResult, GimmeJob}

import scala.util.{Failure, Success}

/**
 * @author ilya40umov
 */
object HttpAccessWorker {
  def props(httpProxy: HttpProxy, config: Config, httpClient: HttpClient) =
    Props(classOf[HttpAccessWorker], httpProxy, config, httpClient)
}

class HttpAccessWorker(httpProxy: HttpProxy, config: Config, httpClient: HttpClient)
  extends Actor with StrictLogging {

  import context.dispatcher

  val maxProxiesToRetry = config.getInt("http.max-proxies-to-retry")

  askForJob()

  override def receive: Receive = expectFetchJob

  def askForJob(): Unit = {
    context.parent ! GimmeJob(httpProxy.proxyId)
    context.become(expectFetchJob)
  }

  def expectFetchJob: Receive = {
    case fetchJob@FetchJob(Fetch(url), replyTo, triedProxyIds) =>
      logger.debug(s"Hitting $url via ${httpProxy.proxyId}")
      httpClient.doGet(url, Some(httpProxy)) pipeTo self
      context.become(expectHttpResponse(fetchJob))
  }

  def expectHttpResponse(fetchJob: FetchJob): Receive = {
    case httpResponse: HttpResponse =>
      fetchJob.replyTo ! FetchResult(fetchJob.fetch, Success(httpResponse))
      askForJob()
    case Status.Failure(cause) if fetchJob.triedProxyIds.size + 1 < maxProxiesToRetry =>
      logger.debug(s"Failed on ${httpProxy.proxyId}. Gonna try on a different one.")
      context.parent ! fetchJob.copy(triedProxyIds = fetchJob.triedProxyIds + httpProxy.proxyId)
      askForJob()
    case Status.Failure(cause) =>
      fetchJob.replyTo ! FetchResult(fetchJob.fetch, Failure(cause))
      askForJob()
  }
}
