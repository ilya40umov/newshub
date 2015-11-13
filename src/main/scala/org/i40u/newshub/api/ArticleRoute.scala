package org.i40u.newshub.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import org.i40u.newshub.storage.ArticleRepository
import org.i40u.newshub.storage.ArticleRepository.ArticleSearch
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

/**
 * @author ilya40umov
 */
class ArticleRoute(articleRepository: ArticleRepository)
                  (implicit val materializer: Materializer, val executionContext: ExecutionContext)
  extends ApiRoute with ApiJsonProtocol {

  override val underlying: Route = {
    pathPrefix("articles") {
      pathEndOrSingleSlash {
        get {
          parameters('keywords ?, 'publishedAfter.as[DateTime] ?, 'from.as[Int] ? 0, 'limit.as[Int] ? 25) {
            (keywords, publishedAfter, from, limit) =>
              complete(
                articleRepository.performSearch(ArticleSearch(keywords, publishedAfter), from, limit)
              )
          }
        }
      }
    }
  }

}
