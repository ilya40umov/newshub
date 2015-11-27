package org.i40u.newshub.storage

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl.RichFuture
import com.sksamuel.elastic4s.jackson.JacksonJson
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.ImmutableSettings
import org.i40u.newshub.Kernel.EventBus
import org.i40u.newshub.{NewsHubSpecLike, SingleSubEventBus}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

/**
 * @author ilya40umov
 */
abstract class RepositorySpec[R <: Repository](actorSystem: ActorSystem)
  extends TestKit(actorSystem) with NewsHubSpecLike with BeforeAndAfterEach with BeforeAndAfterAll {

  var client: ElasticClient = _
  var eventBus: TestProbe = _
  var repository: R = _

  def newRepository(client: ElasticClient, eventBus: EventBus): R

  override protected def beforeAll(): Unit = {
    JacksonJson.mapper.registerModule(new JodaModule())
    FileSystemUtils.deleteRecursively(new File("./tmp/spec-elastic"))
    val settings = ImmutableSettings.settingsBuilder()
      .put("http.enabled", false)
      .put("path.home", "./tmp/spec-elastic")
    client = ElasticClient.local(settings.build)
  }

  override protected def afterAll(): Unit = {
    client.shutdown.await
    TestKit.shutdownActorSystem(system)
  }

  override protected def beforeEach(): Unit = {
    eventBus = TestProbe()
    repository = newRepository(client, new SingleSubEventBus(eventBus.ref))
    repository.createIndex(dropIfExists = true).await
    client.admin.cluster().health(new ClusterHealthRequest().waitForYellowStatus()).actionGet()
  }

}
