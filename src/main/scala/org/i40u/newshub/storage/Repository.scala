package org.i40u.newshub.storage

import scala.concurrent.Future

/**
 * @author ilya40umov
 */
trait Repository {
  /**
   * Creates the index if it does not exist yet.
   * @param dropIfExists tells to drop the index prior to creating(if it already exists)
   */
  def createIndex(dropIfExists: Boolean): Future[Unit]

  /**
   * Triggers index refresh, making all operations performed since the last refresh available for search.
   * Optionally, allows to perform flush before refreshing.
   */
  def refreshIndex(flush: Boolean): Future[Unit]
}
