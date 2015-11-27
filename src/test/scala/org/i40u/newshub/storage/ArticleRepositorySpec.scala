package org.i40u.newshub.storage

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import org.i40u.newshub.Kernel._
import org.i40u.newshub.storage.ArticleRepository.ArticleSearch
import org.i40u.newshub.storage.BaseRepository._
import org.joda.time.DateTime

/**
 * @author ilya40umov
 */
class ArticleRepositorySpec extends RepositorySpec[ArticleRepository](ActorSystem("ArticleRepositorySys")) {

  implicit val ctx = scala.concurrent.ExecutionContext.global

  override def newRepository(client: ElasticClient, eventBus: EventBus): ArticleRepository =
    new ArticleRepositoryImpl(client)

  val article0 = Article("http://watchersonthewall.com/goo304/", Set.empty, "Six is Coming", "", DateTime.now, None)

  def findExistingArticle(url: String): Article = {
    client.execute(get id article0.url from "articles" / "article").await match {
      case getResp if getResp.isExists => getResp.as[Article]
      case _ => throw new IllegalStateException("The article should be in the index!")
    }
  }

  "upsert()" should "allow to insert/update an article depending on whether it's missing or present in the index" in {
    repository.upsert(article0.url) {
      case Some(_) => fail("There should be no such article in the index!")
      case None => article0
    }.await
    findExistingArticle(article0.url).title shouldBe article0.title
    repository.upsert(article0.url) {
      case Some(article) => article.copy(title = "Six is Thinking")
      case None => fail("The article should have been in the index by now!")
    }.await
    findExistingArticle(article0.url).title shouldBe "Six is Thinking"
  }

  def insert(article: Article, flushIndex: Boolean = false): Article = {
    try {
      repository.upsert(article.url) {
        case Some(_) => throw new IllegalStateException("There should be no such article in the index!")
        case None => article
      }.await
    } finally {
      if (flushIndex) {
        repository.refreshIndex(flush = true).await
      }
    }
  }

  "update()" should "allow to update existing articles and fail when there is no indexed article for the url" in {
    intercept[DocumentNotFoundException] { repository.update(article0.url) { a => a }.await }
    insert(article0, flushIndex = true)
    repository.update(article0.url)(_.copy(title = "Seven is NOT coming")).await
    findExistingArticle(article0.url).title shouldBe "Seven is NOT coming"
  }

  "existsForUrl()" should "tell if there is an article in the index that corresponds the given url" in {
    repository.existsForUrl(article0.url).await shouldBe false
    insert(article0, flushIndex = true)
    repository.existsForUrl(article0.url).await shouldBe true
  }

  "performSearch()" should "allow searching over articles by keywords/publish date and limit results" in {
    insert(Article("http://watchersonthewall.com/123/", Set.empty, "One Two Three", "", DateTime.now.minusDays(1), None))
    insert(Article("http://watchersonthewall.com/x/", Set.empty, "X", "", DateTime.now, None), flushIndex = true)
    repository.performSearch(ArticleSearch(), 0, 50).await.size shouldBe 2
    repository.performSearch(ArticleSearch(publishedAfter = Some(DateTime.now().minusHours(1))), 0, 50).await.size shouldBe 1
    repository.performSearch(ArticleSearch(keywords = Some("One")), 0, 50).await.size shouldBe 1
    repository.performSearch(ArticleSearch(keywords = Some("oNe")), 0, 50).await.size shouldBe 1
  }

}
