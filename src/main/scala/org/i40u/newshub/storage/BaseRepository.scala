package org.i40u.newshub.storage


import com.sksamuel.elastic4s.ElasticDsl.{get, _}
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.jackson.JacksonJson._
import com.sksamuel.elastic4s.mappings.TypedFieldDefinition
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, IndexType}
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.index.engine.VersionConflictEngineException

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author ilya40umov
 */
object BaseRepository {

  implicit class RichGetResponse(getResponse: GetResponse) {
    def as[T: Manifest]: T = {
      mapper.readValue[T](getResponse.getSourceAsBytes)
    }
  }

}

abstract class BaseRepository(val client: ElasticClient,
                              val indexAndType: IndexType)(implicit ec: ExecutionContext) extends Repository {

  import BaseRepository._

  def typeMapping: Iterable[TypedFieldDefinition]

  def createMissingIndex(): Future[Unit] = {
    client.execute {
      create index indexAndType.index mappings {
        indexAndType.`type` as typeMapping
      }
    } map { _ => () }
  }

  override def createIndex(dropIfExists: Boolean): Future[Unit] = {
    client.execute(index exists indexAndType.index) flatMap {
      case idxExistsResp if dropIfExists && idxExistsResp.isExists =>
        client.execute(ElasticDsl.delete index indexAndType.index) flatMap (_ => createMissingIndex())
      case idxExistsResp if !dropIfExists && idxExistsResp.isExists =>
        Future.successful((): Unit)
      case _ => createMissingIndex()
    }
  }

  override def refreshIndex(doFlush: Boolean): Future[Unit] = {
    if (doFlush) {
      client.execute(flush index indexAndType.index).map(_ => ())
    } else {
      Future.successful((): Unit)
    } flatMap { _ =>
      client.execute(refresh index indexAndType.index).map(_ => ())
    }
  }

  // for now only retrying only on version conflicts
  def retry[T](times: Int)
              (f: => Future[T]): Future[T] = f recoverWith {
    case _: VersionConflictEngineException if times > 0 => retry(times - 1)(f)
    case _ => f
  }

  def noDocFound[T: Manifest](docId: Any): DocumentNotFoundException =
    new DocumentNotFoundException(s"${manifest[T].runtimeClass.getName} not found for id: $docId")

  def doUpdate[T: Manifest](docId: Any)
                           (update: (T) => T): Future[T] = {
    retry(5) {
      client.execute {
        get id docId from indexAndType
      } flatMap {
        case getResp if getResp.isExists =>
          val obj = getResp.as[T]
          val updatedObj = update(obj)
          client.execute {
            ElasticDsl.update id docId in indexAndType source updatedObj version getResp.getVersion
          } map { _ =>
            updatedObj
          }
        case _ => Future.failed(noDocFound(docId))
      }
    }
  }

  def doUpsert[T: Manifest](docId: Any)
                           (upsert: (Option[T]) => T): Future[T] = {
    retry(5) {
      client.execute {
        get id docId from indexAndType
      } flatMap {
        case getResp if getResp.isExists =>
          val updatedObj = upsert(Some(getResp.as[T]))
          client.execute {
            ElasticDsl.update id docId in indexAndType source updatedObj version getResp.getVersion
          } map { _ => updatedObj }
        case _ =>
          val createdObj = upsert(None)
          client.execute {
            ElasticDsl.index into indexAndType id docId source createdObj
          } map { _ => createdObj }
      }
    }
  }

  def doDelete[T: Manifest](docId: String): Future[T] = {
    client.execute {
      get id docId from indexAndType
    } flatMap {
      case getResp if getResp.isExists =>
        val obj = getResp.as[T]
        client.execute {
          ElasticDsl.delete id docId from indexAndType
        } flatMap {
          case delResp if delResp.isFound => Future.successful(obj)
          case _ => Future.failed(noDocFound(docId))
        }
      case _ => Future.failed(noDocFound(docId))
    }
  }

  def doFindById[T: Manifest](docId: String): Future[Option[T]] = {
    client.execute {
      get id docId from indexAndType
    } map {
      case getResp if getResp.isExists => Some(getResp.as[T])
      case _ => None
    }
  }

}
