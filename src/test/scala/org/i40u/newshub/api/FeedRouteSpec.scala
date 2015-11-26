package org.i40u.newshub.api

import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{MediaTypes, HttpEntity}
import akka.http.scaladsl.model.StatusCodes._
import org.i40u.newshub.storage.{DocumentNotFoundException, Feed, FeedRepository}
import spray.json.{JsString, JsObject}

import scala.concurrent.Future

/**
 * @author ilya40umov
 */
class FeedRouteSpec extends RouteSpec {

  val feed0 = Feed("http://winteriscoming.net/feed/", "Winter is Coming")

  trait FeedRouteFixture {
    val repository = mock[FeedRepository]
    val route = new FeedRoute(repository)
  }

  "GET on /feeds" should "allow searching over feeds" in new FeedRouteFixture {
    (repository.find _).expects(None, 0, 25).returning(Future.successful(Seq(feed0)))
    Get("/feeds") ~> route ~> check {
      status shouldBe OK
      responseAs[Seq[Feed]] shouldBe Seq(feed0)
    }
  }

  "POST on /feeds" should "allow creating a new feed" in new FeedRouteFixture {
    (repository.create _).expects(feed0).returning(Future.successful(feed0))
    val entity = HttpEntity(MediaTypes.`application/json`,
      JsObject("url" -> JsString(feed0.url), "title" -> JsString(feed0.title)).toString())
    Post(s"/feeds", entity) ~> route ~> check {
      status shouldBe Created
      responseAs[Feed] shouldBe feed0
      header("Location") shouldBe Some(Location(s"/${feed0.feedId}"))
    }
  }

  "GET on /feeds/{feedId}" should "allow looking up an existing feed" in new FeedRouteFixture {
    (repository.findById _).expects(feed0.feedId).returning(Future.successful(Some(feed0)))
    Get(s"/feeds/${feed0.feedId}") ~> route ~> check {
      status shouldBe OK
      responseAs[Feed] shouldBe feed0
    }
    (repository.findById _).expects("no-such-feed").returning(Future.successful(None))
    Get("/feeds/no-such-feed") ~> route ~> check {
      status shouldBe NotFound
      responseAs[String] shouldBe "Feed no-such-feed does not exist!"
    }
  }

  "PUT on /feeds/{feedId}" should "allow updating an existing feed" in new FeedRouteFixture {
    (repository.update(_: String)(_ : Feed => Feed)).expects(feed0.feedId, *).
      returning(Future.successful(feed0.copy(title = "Star Wars are coming")))
    val entity = HttpEntity(MediaTypes.`application/json`, JsObject("title" -> JsString("Star Wars are coming")).toString())
    Put(s"/feeds/${feed0.feedId}", entity) ~> route ~> check {
      status shouldBe OK
      responseAs[Feed] shouldBe feed0.copy(title = "Star Wars are coming")
    }
    (repository.update(_: String)(_ : Feed => Feed)).expects("no-such-feed", *).
      returning(Future.failed(new DocumentNotFoundException()))
    Put("/feeds/no-such-feed", entity) ~> route ~> check {
      status shouldBe NotFound
      responseAs[String] shouldBe "Feed no-such-feed does not exist!"
    }
  }

  "DELETE on /feeds/{feedId}" should "allow removing an exiting feed" in new FeedRouteFixture {
    (repository.delete _).expects(feed0.feedId).returning(Future.successful(feed0))
    Delete(s"/feeds/${feed0.feedId}") ~> route ~> check {
      status shouldBe OK
      responseAs[Feed] shouldBe feed0
    }
    (repository.delete _).expects("no-such-feed").returning(Future.failed(new DocumentNotFoundException()))
    Delete("/feeds/no-such-feed") ~> route ~> check {
      status shouldBe NotFound
      responseAs[String] shouldBe "Feed no-such-feed does not exist!"
    }
  }

  "POST on /feeds/flush_index" should "trigger the index flush and refresh" in new FeedRouteFixture {
    (repository.refreshIndex _).expects(true).returning(Future.successful((): Unit))
    Post("/feeds/flush_index") ~> route ~> check {
      status shouldBe OK
      responseAs[String] shouldBe "Index flushed."
    }
  }
}
