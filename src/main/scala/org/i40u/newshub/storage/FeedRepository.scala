package org.i40u.newshub.storage

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import com.sksamuel.elastic4s.{ElasticClient, StandardAnalyzer}

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author ilya40umov
 */
trait FeedRepository {

  def save(feed: Feed): Future[Unit]

  def flushIndex(): Future[Unit]

  def findAll(): Future[Seq[Feed]]
}

class FeedRepositoryImpl(client: ElasticClient)(implicit ec: ExecutionContext) extends FeedRepository {

  if (!client.execute(index exists "feeds").await.isExists) {
    client.execute {
      create index "feeds" mappings {
        "feed" as Seq(
          "url" typed StringType index "analyzed",
          "title" typed StringType index "analyzed" analyzer StandardAnalyzer
        )
      }
    }.await
  }

  override def save(feed: Feed): Future[Unit] = {
    client.execute {
      index into "feeds" / "feed" id feed.url source feed
    }.map(_ => ())
  }

  override def flushIndex(): Future[Unit] = {
    client.execute {
      flush index "feeds"
    } map { _ => () }
  }

  override def findAll(): Future[Seq[Feed]] = {
    client.execute {
      search in "feeds" / "feed" query matchAllQuery limit 10000
    }.map(_.as[Feed])
  }

}
