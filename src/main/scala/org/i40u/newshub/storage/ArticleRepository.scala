package org.i40u.newshub.storage

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.jackson.JacksonJson._
import com.sksamuel.elastic4s.mappings.FieldType.{DateType, StringType}
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, StandardAnalyzer}
import com.typesafe.scalalogging.StrictLogging
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.index.engine.VersionConflictEngineException

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
 * @author ilya40umov
 */
trait ArticleRepository {

  def update(url: String)(update: Article => Article): Future[Article]

  def upsert(url: String)(upsert: Option[Article] => Article): Future[Article]

  def existsForUrl(url: String): Future[Boolean]

  def performSearch(textQuery: String, from: Int, limit: Int): Future[Seq[Article]]

}

class ArticleRepositoryImpl(client: ElasticClient)(implicit ec: ExecutionContext)
  extends ArticleRepository with StrictLogging {

  if (!client.execute(index exists "articles").await.isExists) {
    client.execute {
      create index "articles" mappings {
        "article" as Seq(
          "url" typed StringType index NotAnalyzed,
          "origUrls" typed StringType index NotAnalyzed,
          "title" typed StringType index "analyzed" analyzer StandardAnalyzer,
          "content" typed StringType index "analyzed" analyzer StandardAnalyzer,
          "pubDate" typed DateType index NotAnalyzed,
          "imageUrl" typed StringType index NotAnalyzed
        )
      }
    }.await
  }

  implicit class RichGetResponse(getResponse: GetResponse) {
    def as[T: Manifest]: T = mapper.readValue[T](getResponse.getSourceAsBytes)
  }

  // for now only retrying on version conflicts
  def retry[T](times: Int)(f: => Future[T]): Future[T] = f recoverWith {
    case _: VersionConflictEngineException if times > 0 => retry(times - 1)(f)
    case _ => f
  }

  override def update(url: String)(update: (Article) => Article): Future[Article] = {
    retry(5) {
      client.execute {
        get id url from "articles" / "article"
      } flatMap { getResp =>
        if (getResp.isExists) {
          val article = getResp.as[Article]
          val updatedArticle = update(article)
          client.execute {
            ElasticDsl.update id url in "articles" / "article" source updatedArticle version getResp.getVersion
          } map { _ =>
            updatedArticle
          }
        } else {
          Future.failed(new DocumentNotFoundException(s"Article not found for URL: $url"))
        }
      }
    }
  }

  override def upsert(url: String)(upsert: (Option[Article]) => Article): Future[Article] = {
    retry(5) {
      client.execute {
        get id url from "articles" / "article"
      } flatMap { getResp =>
        val article = if (getResp.isExists) Some(getResp.as[Article]) else None
        val updatedArticle = upsert(article)
        if (getResp.isExists) {
          client.execute {
            ElasticDsl.update id url in "articles" / "article" source updatedArticle version getResp.getVersion
          } map { _ => updatedArticle }
        } else {
          client.execute {
            ElasticDsl.index into "articles" / "article" id url source updatedArticle
          } map { _ => updatedArticle }
        }
      }
    }
  }

  override def existsForUrl(url: String): Future[Boolean] = {
    client.execute {
      search in "articles" / "article" query {
        bool (
          should (
            termQuery("url", url),
            termQuery("origUrls", url)
          )
        )
      } limit 1 fetchSource true fields("url", "origUrls")
    } map { response =>
      response.hits.headOption.isDefined
    }
  }

  override def performSearch(textQuery: String, _from: Int, _limit: Int): Future[Seq[Article]] = {
    client.execute {
      search in "articles" / "article" query {
        if (textQuery.nonEmpty) {
          bool (
            should (
              stringQuery(textQuery) analyzer StandardAnalyzer field "title" boost 4,
              stringQuery(textQuery) analyzer StandardAnalyzer field "content"
            )
          ) minimumShouldMatch 1
        } else {
          matchAllQuery
        }
      } from _from limit _limit
    }.map(_.as[Article])
  }

}
