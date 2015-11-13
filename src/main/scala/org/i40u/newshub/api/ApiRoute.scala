package org.i40u.newshub.api

import akka.http.scaladsl.server.{RouteResult, RequestContext, Route}

import scala.concurrent.Future
import scala.language.implicitConversions

/**
 * @author ilya40umov
 */
trait ApiRoute extends Route {

  def underlying: Route

  override def apply(rc: RequestContext): Future[RouteResult] = underlying(rc)
}
