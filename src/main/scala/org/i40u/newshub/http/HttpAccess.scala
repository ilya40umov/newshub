package org.i40u.newshub.http

import akka.actor.ActorRef
import com.ning.http.client.{AsyncHttpClient, AsyncHttpClientConfig}
import org.i40u.newshub.Kernel

/**
 * @author ilya40umov
 */
trait HttpAccess {

  def httpClient: HttpClient

  def httpAccessManager: ActorRef
}

trait DefaultHttpAccess extends HttpAccess {
  self: Kernel =>
  lazy val asyncHttpClient = {
    val config = new AsyncHttpClientConfig.Builder().
      setConnectTimeout(30000).
      setRequestTimeout(30000).
      setReadTimeout(30000).
      setMaxConnections(-1).
      setMaxConnectionsPerHost(-1).
      setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:34.0) Gecko/20100101 Firefox/34.0").
      setIOThreadMultiplier(Runtime.getRuntime.availableProcessors()).
      setFollowRedirect(true).
      setMaxRedirects(7).
      setAcceptAnyCertificate(true).
      build()
    new AsyncHttpClient(config)
  }

  override lazy val httpClient: HttpClient = new HttpClientImpl(asyncHttpClient)

  override lazy val httpAccessManager: ActorRef =
    system.actorOf(HttpAccessManager.props(config, httpClient), "httpAccessManager")
}