package org.i40u.newshub.storage

import org.joda.time.DateTime

/**
 * @author ilya40umov
 */
case class Article(url: String,
                   origUrls: Set[String],
                   title: String,
                   content: String,
                   pubDate: DateTime,
                   imageUrl: Option[String])
