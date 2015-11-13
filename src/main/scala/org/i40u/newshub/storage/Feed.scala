package org.i40u.newshub.storage

import org.i40u.newshub.md5Hash

/**
 * @author ilya40umov
 */
object Feed {

  def apply(url: String, title: String): Feed = Feed(md5Hash(url), url, title)

}

case class Feed(feedId: String,
                url: String,
                title: String)
