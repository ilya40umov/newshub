package org.i40u.newshub.crawler

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Date

import com.rometools.modules.mediarss.{MediaEntryModule, MediaModule}
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.{SyndFeedInput, XmlReader}
import com.typesafe.scalalogging.StrictLogging
import org.i40u.newshub.crawler.FeedParser.{FeedItem, ParsedFeed}
import org.joda.time.DateTime

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.util.{Failure, Success, Try}

/**
 * @author ilya40umov
 */
case object FeedParser {

  case class ParsedFeed(title: String, feedItems: Seq[FeedItem])

  case class FeedItem(url: String, title: String, pubDate: DateTime, imageUrl: Option[String])

}

trait FeedParser {

  def parse(feedUrl: String, feedBody: String): Try[ParsedFeed]

}

class FeedParserImpl extends FeedParser with StrictLogging {

  import com.rometools.utils.Strings._

  override def parse(feedUrl: String, feedBody: String): Try[ParsedFeed] = Try {
    val reader = new XmlReader(new ByteArrayInputStream(feedBody.getBytes(StandardCharsets.UTF_8)))
    val feed = new SyndFeedInput().build(reader)
    val feedTitle = trimToEmpty(feed.getTitle)
    val feedItems = feed.getEntries.asScala.flatMap { entry =>
      val url = trimToEmpty(entry.getLink)
      Try(com.netaporter.uri.Uri.parse(url)) match {
        case Success(_) =>
          val title = trimToEmpty(entry.getTitle)
          val pubDate = new DateTime(Seq(entry.getUpdatedDate, entry.getPublishedDate).find(_ != null).getOrElse(new Date))
          val imageUrl = getImageUrl(entry)
          Some(FeedItem(url, title, pubDate, imageUrl))
        case Failure(exception) =>
          logger.warn("[{}] Ignoring an invalid RSS entry with URL: {}", feedUrl, url)
          None
      }
    }.toList
    ParsedFeed(feedTitle, feedItems)
  }

  def getImageUrl(entry: SyndEntry): Option[String] = {

    def imageFromMediaModule = Option(entry.getModule(MediaModule.URI)) match {
      case Some(mem: MediaEntryModule) =>
        mem.getMediaContents.find { mc =>
          (Option(mc.getType).exists(_.contains("image")) || Option(mc.getMedium).exists(_.contains("image"))) &&
            Option(mc.getReference).map(_.toString).exists(url => !isBlank(url) && url.toLowerCase.endsWith(".jpg"))
        } map { mc => mc.getReference.toString }
      case _ => None
    }

    def imageFromEnclosures = entry.getEnclosures.asScala.find { enc =>
      Option(enc.getType).exists(_.contains("image")) &&
      Option(enc.getUrl).exists(_.toLowerCase.endsWith(".jpg"))
    } map { enc => enc.getUrl }

    imageFromMediaModule orElse imageFromEnclosures
  }

}
