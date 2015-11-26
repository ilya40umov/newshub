package org.i40u.newshub

import org.i40u.newshub.api.DefaultApi
import org.i40u.newshub.crawler.DefaultCrawler
import org.i40u.newshub.http.DefaultHttpAccess
import org.i40u.newshub.storage.DefaultStorage

/**
 * @author ilya40umov
 */
object NewsHubApp
  extends App
  with DefaultKernel with DefaultStorage with DefaultHttpAccess with DefaultCrawler with DefaultApi {

  //  Await.ready(feedRepository.save(Feed("http://winteriscoming.net/feed/", "Winter is Coming")), 1.minute)
  //  Await.ready(feedRepository.flushIndex(), 1.minute)

  waitForStorageToGetReady()
  //  kickOffCrawler()
  startUpApi()

}