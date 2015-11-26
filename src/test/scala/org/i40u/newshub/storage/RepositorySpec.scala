package org.i40u.newshub.storage

import java.io.File

import com.fasterxml.jackson.datatype.joda.JodaModule
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl.RichFuture
import com.sksamuel.elastic4s.jackson.JacksonJson
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.ImmutableSettings
import org.i40u.newshub.NewsHubSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

/**
 * @author ilya40umov
 */
trait RepositorySpec[R <: Repository] extends NewsHubSpec with BeforeAndAfterEach with BeforeAndAfterAll {

  var client: ElasticClient = _
  var repository: R = _

  def newRepository(client: ElasticClient): R

  override protected def beforeAll(): Unit = {
    JacksonJson.mapper.registerModule(new JodaModule())
    FileSystemUtils.deleteRecursively(new File("./tmp/spec-elastic"))
    val settings = ImmutableSettings.settingsBuilder()
      .put("http.enabled", false)
      .put("path.home", "./tmp/spec-elastic")
    client = ElasticClient.local(settings.build)
    repository = newRepository(client)
  }

  override protected def afterAll(): Unit = {
    client.shutdown.await
  }

  override protected def beforeEach(): Unit = {
    repository.createIndex(dropIfExists = true).await
    client.admin.cluster().health(new ClusterHealthRequest().waitForYellowStatus()).actionGet()
  }

}
