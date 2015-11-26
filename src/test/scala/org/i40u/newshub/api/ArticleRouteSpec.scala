package org.i40u.newshub.api

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri
import org.i40u.newshub.storage.ArticleRepository.ArticleSearch
import org.i40u.newshub.storage.{Article, ArticleRepository}
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author ilya40umov
 */
class ArticleRouteSpec extends RouteSpec {

  val article0 = Article("http://watchersonthewall.com/goo304/", Set.empty, "Six is Coming", "", DateTime.now, None)
  val article1 = Article("http://one.com/", Set.empty, "One", "", DateTime.now.minusDays(1), None)
  val article2 = Article("http://two.com/", Set.empty, "Two", "", DateTime.now.minusDays(2), None)

  "GET on /articles" should "allow searching over the articles index" in {
    val repository = mock[ArticleRepository]
    (repository.performSearch _).expects(ArticleSearch(), 0, 25).
      returning(Future.successful(Seq(article2, article1, article0)))
    (repository.performSearch _).expects(ArticleSearch(keywords = Some("One")), 0, 25).
      returning(Future.successful(Seq(article1)))
    val pubAfter = DateTime.now().minusHours(25)
    (repository.performSearch _).expects(ArticleSearch(publishedAfter = Some(pubAfter)), 0, 25).
      returning(Future.successful(Seq(article1, article0)))
    val route = new ArticleRoute(repository)
    Get("/articles") ~> route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[Seq[Article]] shouldBe Seq(article2, article1, article0)
    }
    Get(Uri("/articles").withQuery("keywords" -> "One")) ~> route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[Seq[Article]] shouldBe Seq(article1)
    }
    Get(Uri("/articles").withQuery("publishedAfter" -> DateJsonFormat.isoDateFormat.print(pubAfter))) ~> route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[Seq[Article]] shouldBe Seq(article1, article0)
    }
  }

}
