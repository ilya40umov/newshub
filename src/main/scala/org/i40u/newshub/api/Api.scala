package org.i40u.newshub.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.Kernel
import org.i40u.newshub.storage.Storage

/**
 * @author ilya40umov
 */
trait Api {

  def startUpApi(): Unit

}

trait DefaultApi extends Api with StrictLogging {
  self: Kernel with Storage =>

  implicit lazy val materializer = ActorMaterializer()

  lazy val feedRoute = new FeedRoute(feedRepository)
  lazy val articleRoute = new ArticleRoute(articleRepository)

  lazy val v1Routes =
    pathPrefix("v1") {
      feedRoute ~ articleRoute
    }

  override def startUpApi(): Unit = {
    logger.info("Starting up REST API.")
    Http().bindAndHandle(v1Routes, config.getString("api.interface"), config.getInt("api.port"))
  }

}
