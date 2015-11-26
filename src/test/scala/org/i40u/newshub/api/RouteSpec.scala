package org.i40u.newshub.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.i40u.newshub.NewsHubSpec

/**
 * @author ilya40umov
 */
trait RouteSpec extends NewsHubSpec with ScalatestRouteTest with ApiJsonProtocol
