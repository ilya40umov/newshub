package org.i40u.newshub.storage

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import com.sksamuel.elastic4s.{ElasticClient, StandardAnalyzer}
import org.elasticsearch.action.index.IndexRequest.OpType
import org.elasticsearch.transport.RemoteTransportException
import org.i40u.newshub.Kernel.EventBus

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * @author ilya40umov
 */
trait FeedRepository extends Repository {

  def create(feed: Feed): Future[Feed]

  def update(feedId: String)(update: (Feed) => Feed): Future[Feed]

  def delete(feedId: String): Future[Feed]

  def findById(feedId: String): Future[Option[Feed]]

  def find(title: Option[String], from: Int, limit: Int): Future[Seq[Feed]]

}

class FeedRepositoryImpl(client: ElasticClient, eventBus: EventBus)(implicit ec: ExecutionContext)
  extends BaseRepository(client, "feeds" / "feed") with FeedRepository {

  override val typeMapping = Seq(
    "feedId" typed StringType index NotAnalyzed,
    "url" typed StringType index NotAnalyzed,
    "title" typed StringType index "analyzed" analyzer StandardAnalyzer
  )

  override def create(feed: Feed): Future[Feed] = {
    import org.elasticsearch.index.engine.{DocumentAlreadyExistsException => DAEException}
    client.execute {
      index into indexAndType id feed.feedId source feed opType OpType.CREATE
    }.transform({ _ =>
      feed
    }, {
      case dae: DAEException =>
        new DocumentAlreadyExistsException(dae.getMessage, dae)
      case rte: RemoteTransportException if rte.getCause != null && rte.getCause.getClass == classOf[DAEException] =>
        new DocumentAlreadyExistsException(rte.getCause.getMessage, rte.getCause)
      case e => e
    }) andThen {
      case Success(_) => eventBus.publish(FeedCreated(feed.feedId, feed.url))
    }
  }

  override def update(feedId: String)(update: (Feed) => Feed): Future[Feed] = doUpdate[Feed](feedId)(update)

  override def delete(feedId: String): Future[Feed] = {
    doDelete[Feed](feedId) andThen {
      case Success(_) => eventBus.publish(FeedDeleted(feedId))
    }
  }

  override def findById(feedId: String): Future[Option[Feed]] = doFindById[Feed](feedId)

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

}
