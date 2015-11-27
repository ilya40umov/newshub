package org.i40u.newshub.storage

/**
 * @author ilya40umov
 */
sealed trait StorageEvent

case class FeedCreated(feedId: String, url: String) extends StorageEvent

case class FeedDeleted(feedId: String) extends StorageEvent

