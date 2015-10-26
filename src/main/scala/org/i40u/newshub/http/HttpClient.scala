package org.i40u.newshub.http


import com.ning.http.client.{AsyncCompletionHandler, AsyncHttpClient, RequestBuilder, Response}

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * @author ilya40umov
 */
trait HttpClient {
  def doGet[T](url: String, httpProxy: Option[HttpProxy] = None)
              (implicit userAgentProvider: () => String = RandomUserAgentProvider): Future[HttpResponse]
}

class HttpClientImpl(asyncHttpClient: AsyncHttpClient)(implicit executor: ExecutionContext) extends HttpClient {
  override def doGet[T](url: String, httpProxy: Option[HttpProxy])
                       (implicit userAgentProvider: () => String): Future[HttpResponse] = {
    try {
      val promise = Promise[HttpResponse]()
      val rb = new RequestBuilder().setUrl(url).setHeader("User-Agent", userAgentProvider())
      httpProxy.foreach(proxy => rb.setProxyServer(proxy.proxyServer))
      val request = rb.build()
      asyncHttpClient.executeRequest(request, new AsyncCompletionHandler[Response] {

        override def onCompleted(response: Response): Response = {
          try {
            val status = response.getStatusCode
            val body = response.getResponseBody
            val finalUrl = response.getUri.toUrl
            promise.trySuccess(HttpResponse(status, body, finalUrl))
          } catch {
            case t: Exception => promise.tryFailure(t)
          }
          response
        }

        override def onThrowable(t: Throwable): Unit = {
          promise.tryFailure(t)
        }
      })
      promise.future
    } catch {
      case t: Exception => Future.failed(t)
    }
  }
}