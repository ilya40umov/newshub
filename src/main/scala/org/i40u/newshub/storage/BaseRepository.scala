package org.i40u.newshub.storage


import com.sksamuel.elastic4s.ElasticDsl.{get, _}
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.jackson.JacksonJson._
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, IndexType}
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.index.engine.VersionConflictEngineException

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author ilya40umov
 */
abstract class BaseRepository {

  implicit val client: ElasticClient
  implicit val executionContext: ExecutionContext
  implicit val indexAndType: IndexType

  implicit class RichGetResponse(getResponse: GetResponse) {
    def as[T: Manifest]: T = {
      mapper.readValue[T](getResponse.getSourceAsBytes)
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

  def update[T: Manifest](docId: Any)
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

  def upsert[T: Manifest](docId: Any)
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

  def delete[T: Manifest](docId: String): Future[T] = {
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

  def findById[T: Manifest](docId: String): Future[Option[T]] = {
    client.execute {
      get id docId from indexAndType
    } map {
      case getResp if getResp.isExists => Some(getResp.as[T])
      case _ => None
    }
  }

}
