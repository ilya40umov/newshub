package org.i40u.newshub.http

import scala.util.Random

/**
 * @author ilya40umov
 */
object RandomUserAgentProvider extends (() => String) {

  val userAgents = Vector(
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_3) AppleWebKit/600.6.3 (KHTML, like Gecko) Version/8.0.6 Safari/600.6.3",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.124 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:34.0) Gecko/20100101 Firefox/34.0"
  )

  override def apply(): String = userAgents(Random.nextInt(userAgents.size))
}
