package org.i40u.newshub.storage

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl.RichFuture
import org.i40u.newshub.Kernel.EventBus

/**
 * @author ilya40umov
 */
class FeedRepositorySpec extends RepositorySpec[FeedRepository](ActorSystem("FeedRepositorySys")) {

  implicit val ctx = scala.concurrent.ExecutionContext.global

  override def newRepository(client: ElasticClient, eventBus: EventBus): FeedRepository =
    new FeedRepositoryImpl(client, eventBus)

  val feed0 = Feed("http://winteriscoming.net/feed/", "Winter is Coming")

  "create()" should "allow indexing new feeds and fail when feed already exists" in {
    repository.findById(feed0.feedId).await shouldBe None
    repository.create(feed0).await shouldBe feed0
    eventBus.expectMsg(FeedCreated(feed0.feedId, feed0.url))
    repository.findById(feed0.feedId).await shouldBe Some(feed0)
    intercept[DocumentAlreadyExistsException] { repository.create(feed0).await }
  }

  "update()" should "allow updating existing feeds and fail when feed to be updated does not exist" in {
    intercept[DocumentNotFoundException] { repository.update(feed0.feedId)(feed => feed).await }
    repository.create(feed0).await
    repository.update(feed0.feedId)(feed => feed.copy(title = "Winterfell")).await.title shouldBe "Winterfell"
  }

  "delete()" should "allow removing existing feeds and fail for those that don't exist" in {
    intercept[DocumentNotFoundException] { repository.delete(feed0.feedId).await }
    repository.create(feed0).await
    eventBus.expectMsg(FeedCreated(feed0.feedId, feed0.url))
    repository.delete(feed0.feedId).await shouldBe feed0
    eventBus.expectMsg(FeedDeleted(feed0.feedId))
  }

  "find()" should "allow to find feeds optionally filtering by title" in {
    repository.create(Feed("http://feed1.com", "Feed About Left"))
    repository.create(Feed("http://feed2.com", "Feed About Right"))
    repository.create(Feed("http://feed3.com", "Feed About Who Is Right"))
    while (repository.find(title = None, 0, 50).await.size != 3) {
      repository.refreshIndex(flush = true).await
    }
    repository.find(title = None, 0, 50).await.size shouldBe 3
    repository.find(title = Some("Left"), 0, 50).await.size shouldBe 1
    repository.find(title = Some("Right"), 0, 50).await.size shouldBe 2
    repository.find(title = Some("Feed"), 0, 50).await.size shouldBe 3
    repository.find(title = Some("Zorro"), 0, 50).await.size shouldBe 0
  }

}
