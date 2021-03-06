package org.i40u.newshub.storage

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.mappings.FieldType.{DateType, StringType}
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.storage.ArticleRepository.ArticleSearch
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
 * @author ilya40umov
 */
object ArticleRepository {

  case class ArticleSearch(keywords: Option[String] = None,
                           publishedAfter: Option[DateTime] = None)

}

trait ArticleRepository extends Repository {

  def update(url: String)(update: Article => Article): Future[Article]

  def upsert(url: String)(upsert: Option[Article] => Article): Future[Article]

  def existsForUrl(url: String): Future[Boolean]

  def performSearch(searchCriteria: ArticleSearch, from: Int, limit: Int): Future[Seq[Article]]

}

class ArticleRepositoryImpl(client: ElasticClient)(implicit ec: ExecutionContext)
  extends BaseRepository(client, "articles" / "article") with ArticleRepository with StrictLogging {

  override val typeMapping = Seq(
    "url" typed StringType index NotAnalyzed,
    "origUrls" typed StringType index NotAnalyzed,
    "title" typed StringType index "analyzed" analyzer StandardAnalyzer,
    "content" typed StringType index "analyzed" analyzer StandardAnalyzer,
    "pubDate" typed DateType index NotAnalyzed,
    "imageUrl" typed StringType index NotAnalyzed
  )

  override def update(url: String)(update: (Article) => Article): Future[Article] = doUpdate(url)(update)

  override def upsert(url: String)(upsert: (Option[Article]) => Article): Future[Article] = doUpsert(url)(upsert)

  override def existsForUrl(url: String): Future[Boolean] = {
    client.execute {
      search in indexAndType query {
        bool(
          should(
            termQuery("url", url),
            termQuery("origUrls", url)
          )
        )
      } limit 1 fetchSource true fields("url", "origUrls")
    } map { response =>
      response.hits.headOption.isDefined
    }
  }

  override def performSearch(searchCriteria: ArticleSearch, qFrom: Int, qLimit: Int): Future[Seq[Article]] = {
    client.execute {
      search in indexAndType query {
        searchCriteria match {
          case ArticleSearch(None, None) =>
            matchAllQuery
          case ArticleSearch(keywords, publishedAfter) =>
            val boolQuery = new BoolQueryDefinition
            publishedAfter foreach { pubAfter =>
              boolQuery.must(rangeQuery("pubDate") from pubAfter includeLower true)
            }
            keywords foreach { textQuery =>
              boolQuery.should(
                stringQuery(textQuery) analyzer StandardAnalyzer field "title" boost 4,
                stringQuery(textQuery) analyzer StandardAnalyzer field "content"
              )
            }
            boolQuery
        }
      } from qFrom limit qLimit
    }.map(_.as[Article])
  }

}
