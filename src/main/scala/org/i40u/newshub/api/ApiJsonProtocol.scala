package org.i40u.newshub.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling._
import org.i40u.newshub.storage.{Article, Feed}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import spray.json._

/**
 * @author ilya40umov
 */
object ApiJsonProtocol extends ApiJsonProtocol

trait ApiJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object DateJsonFormat extends RootJsonFormat[DateTime] {

    val isoDateFormat = ISODateTimeFormat.dateTimeNoMillis()

    override def write(obj: DateTime) = JsString(isoDateFormat.print(obj))

    override def read(json: JsValue): DateTime = json match {
      case JsString(s) => isoDateFormat.parseDateTime(s)
      case _ => throw new DeserializationException(s"Parsing of $json into DateTime is not supported!")
    }
  }

  implicit val dateTimeUnmarshaller: FromStringUnmarshaller[DateTime] =
    Unmarshaller.strict { string: String => DateJsonFormat.isoDateFormat.parseDateTime(string) }

  implicit val articleFormat = jsonFormat6(Article)
  implicit val feedFormat: RootJsonFormat[Feed] = jsonFormat3(Feed.apply)

  //
  // The following implicits are only here because Intellij IDEA keeps showing errors without them
  // (but code compiles and runs just OK without them).
  //
  implicit val articlesWriter = seqFormat[Article]
  implicit val feedsWriter = seqFormat[Feed]

}
