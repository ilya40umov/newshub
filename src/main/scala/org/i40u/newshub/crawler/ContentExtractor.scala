package org.i40u.newshub.crawler

import com.gravity.goose.{Configuration, Goose}

import scala.util.Try

/**
 * @author ilya40umov
 */
trait ContentExtractor {

  def extractText(url: String, rawHtml: String): Try[String]

}

class GooseContentExtractor extends ContentExtractor {
  val goose = {
    val config = new Configuration()
    config.setEnableImageFetching(false)
    new Goose(config)
  }

  override def extractText(url: String, rawHtml: String): Try[String] = Try {
    val article = goose.extractContent(url, rawHtml)
    article.cleanedArticleText
  }

}
