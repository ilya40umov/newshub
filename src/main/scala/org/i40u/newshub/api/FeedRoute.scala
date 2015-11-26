package org.i40u.newshub.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.Materializer
import org.i40u.newshub.md5Hash
import org.i40u.newshub.storage.{DocumentNotFoundException, Feed, FeedRepository}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

/**
 * @author ilya40umov
 */
object FeedRoute {

  case class FeedCreate(url: String, title: String)

  case class FeedUpdate(title: Option[String])

}

class FeedRoute(feedRepository: FeedRepository)
               (implicit val materializer: Materializer, val executionContext: ExecutionContext)
  extends ApiRoute with ApiJsonProtocol {

  import FeedRoute._

  implicit val feedCreate = jsonFormat2(FeedCreate)
  implicit val feedUpdate = jsonFormat1(FeedUpdate)

  def feedNotFound(feedId: String): ToResponseMarshallable = NotFound -> s"Feed $feedId does not exist!"

  override val underlying: Route = {
    pathPrefix("feeds") {
      pathEndOrSingleSlash {
        get {
          parameters('title ?, 'from.as[Int] ? 0, 'limit.as[Int] ? 25) { (title, from, limit) =>
            complete(
              feedRepository.find(title, from, limit)
            )
          }
        } ~
          post {
            entity(as[FeedCreate]) { feedCreate =>
              val feedId = md5Hash(feedCreate.url)
              respondWithHeader(Location(s"/$feedId")) {
                complete {
                  Created -> feedRepository.create(Feed(feedId, feedCreate.url, feedCreate.title))
                }
              }
            }
          }
      } ~
        pathPrefix(Segment) { feedId =>
          pathEndOrSingleSlash {
            get {
              complete {
                feedRepository.findById(feedId).map[ToResponseMarshallable] {
                  case Some(feed) => feed
                  case None => feedNotFound(feedId)
                }
              }
            } ~
              handleExceptions(
                ExceptionHandler { case _: DocumentNotFoundException => complete(feedNotFound(feedId)) }
              ) {
                put {
                  entity(as[FeedUpdate]) { feedUpdate =>
                    complete {
                      feedRepository.update(feedId) { feed =>
                        feed.copy(
                          title = feedUpdate.title.getOrElse(feed.title)
                        )
                      }
                    }
                  }
                } ~
                  delete {
                    complete {
                      feedRepository.delete(feedId)
                    }
                  }
              }
          }
        } ~
        pathPrefix("flush_index") {
          post {
            complete {
              feedRepository.refreshIndex(flush = true).map {
                case _ => OK -> "Index flushed."
              }
            }
          }
        }
    }
  }

}
