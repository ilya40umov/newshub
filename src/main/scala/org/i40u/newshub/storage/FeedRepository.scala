package org.i40u.newshub.storage

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, StandardAnalyzer}
import org.elasticsearch.action.index.IndexRequest.OpType

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author ilya40umov
 */
trait FeedRepository {

  def create(feed: Feed): Future[Feed]

  def update(feedId: String)(update: (Feed) => Feed): Future[Feed]

  def delete(feedId: String): Future[Feed]

  def findById(feedId: String): Future[Option[Feed]]

  def find(title: Option[String], from: Int, limit: Int): Future[Seq[Feed]]

  def flushIndex(): Future[Unit]
}

class FeedRepositoryImpl(cli: ElasticClient, ec: ExecutionContext)
  extends BaseRepository with FeedRepository {

  override implicit val client = cli
  override implicit val executionContext = ec
  override implicit val indexAndType = "feeds" / "feed"

  if (!client.execute(index exists indexAndType.index).await.isExists) {
    client.execute {
      ElasticDsl.create index indexAndType.index mappings {
        indexAndType.`type` as Seq(
          "feedId" typed StringType index NotAnalyzed,
          "url" typed StringType index NotAnalyzed,
          "title" typed StringType index "analyzed" analyzer StandardAnalyzer
        )
      }
    }.await
  }

  override def create(feed: Feed): Future[Feed] = {
    client.execute {
      index into indexAndType id feed.feedId source feed opType OpType.CREATE
    }.map(_ => feed)
  }

  override def update(feedId: String)(update: (Feed) => Feed): Future[Feed] =
    super[BaseRepository].update[Feed](feedId)(update)

  override def delete(feedId: String): Future[Feed] =
    super[BaseRepository].delete[Feed](feedId)

  override def findById(feedId: String): Future[Option[Feed]] =
    super[BaseRepository].findById[Feed](feedId)

  override def find(mbTitle: Option[String], qFrom: Int, qLimit: Int): Future[Seq[Feed]] = {
    client.execute {
      search in indexAndType query {
        mbTitle match {
          case Some(title) => bool(
            must(
              stringQuery(title) analyzer StandardAnalyzer field "title"
            )
          )
          case None => matchAllQuery
        }
      } from qFrom limit qLimit
    }.map(_.as[Feed])
  }

  override def flushIndex(): Future[Unit] = {
    client.execute {
      flush index indexAndType.index
    } map { _ => () }
  }

}
