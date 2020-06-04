package uk.gov.justice.digital.hmpps.prisonersearch

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.google.gson.Gson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.ResponseException
import org.elasticsearch.client.RestHighLevelClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerA
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerB
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria
import java.lang.Thread.sleep


@ActiveProfiles(profiles = ["test", "test-queue"])
abstract class QueueIntegrationTest : IntegrationTest() {

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Autowired
  lateinit var queueUrl: String

  @Autowired
  lateinit var indexQueueUrl: String

  @Autowired
  lateinit var gson: Gson

  @Autowired
  lateinit var elasticSearchClient: RestHighLevelClient

  @Autowired
  lateinit var elasticsearchOperations: ElasticsearchOperations

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }


  fun prisonRequestCountFor(url: String) = prisonMockServer.findAll(getRequestedFor(urlEqualTo(url))).count()

  fun indexPrisoners() {
    webTestClient.put().uri("/prisoner-index/build-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089EY") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089EZ") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FA") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FB") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FC") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FX") } matches { it == 1 }

    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
  }

  fun setupIndexes() {
    waitForDomain()
    createIndexStatusIndex()
    createPrisonerIndex(SyncIndex.INDEX_A)
    createPrisonerIndex(SyncIndex.INDEX_B)
  }

  private fun waitForDomain() {
    var retry = 0
    do {
      var response: Response? = null
      try {
        response = elasticSearchClient.lowLevelClient.performRequest(Request("GET", "/"))
      } catch (e: ResponseException) {
      }
      retry += 1
      if (retryEsCheck(retry, response)) {
        log.debug("Waiting for ES Domain to be available...")
        sleep(5000)
      }
    } while (retryEsCheck(retry, response))
  }

  private fun retryEsCheck(retry: Int, response: Response?) =
      retry < 15 && (response == null || response.statusLine.statusCode != 200)

  private fun createIndexStatusIndex() {
    val response = elasticSearchClient.lowLevelClient.performRequest(Request("HEAD", "/offender-index-status"))
    if (response.statusLine.statusCode == 404) {
      val indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of("offender-index-status"))
      indexOperations.create()
      indexOperations.putMapping(indexOperations.createMapping(IndexStatus::class.java))
    }
    val resetIndexStatus = Request("PUT", "/offender-index-status/_doc/STATUS")
    resetIndexStatus.setJsonEntity(gson.toJson(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false)))
    elasticSearchClient.lowLevelClient.performRequest(resetIndexStatus)
  }

  private fun createPrisonerIndex(prisonerIndex: SyncIndex) {
    val response = elasticSearchClient.lowLevelClient.performRequest(Request("HEAD", "/${prisonerIndex.indexName}"))
    if (response.statusLine.statusCode == 404) {
      val indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(prisonerIndex.indexName))
      indexOperations.create()
      indexOperations.putMapping(indexOperations.createMapping(if (prisonerIndex == SyncIndex.INDEX_A) PrisonerA::class.java else PrisonerB::class.java))
    }
  }

  fun search(searchCriteria: SearchCriteria, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/match")
      .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

}

private fun String.readResourceAsText(): String = QueueIntegrationTest::class.java.getResource(this).readText()