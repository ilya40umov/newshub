package org.i40u.newshub.storage

import com.fasterxml.jackson.datatype.joda.JodaModule
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.jackson.JacksonJson
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.common.settings.ImmutableSettings
import org.i40u.newshub.Kernel

/**
 * @author ilya40umov
 */
trait Storage {

  def waitForStorageToPrepare(): Unit

  def feedRepository: FeedRepository

  def articleRepository: ArticleRepository
}

trait DefaultStorage extends Storage {
  self: Kernel =>

  JacksonJson.mapper.registerModule(new JodaModule())

  lazy val settings = ImmutableSettings.settingsBuilder()
    .put("http.enabled", false)
    .put("path.home", s"${home.getAbsolutePath}/elastic/")
  lazy val client = ElasticClient.local(settings.build)

  override lazy val articleRepository = new ArticleRepositoryImpl(client, executionContext)
  override lazy val feedRepository = new FeedRepositoryImpl(client, executionContext)

  override def waitForStorageToPrepare(): Unit = {
    // XXX perhaps it makes sense to call repositories here so that they can ensure all indexes are in place
    // and only then wait for yellow status
    client.admin.cluster().health(new ClusterHealthRequest().waitForYellowStatus()).actionGet()
  }
}