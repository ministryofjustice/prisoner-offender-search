package uk.gov.justice.digital.hmpps.prisonersearch

import com.amazonaws.services.sqs.AmazonSQS
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.config.IndexProperties
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerA
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerB
import uk.gov.justice.digital.hmpps.prisonersearch.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexQueueService
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonSearch
import uk.gov.justice.digital.hmpps.prisonersearch.services.RestrictedPatientSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerDetailRequest

@ActiveProfiles(profiles = ["test", "test-queue", "stdout"])
abstract class QueueIntegrationTest : IntegrationTest() {

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  @Qualifier("eventqueue-sqs-dlq-client")
  lateinit var eventQueueSqsDlqClient: AmazonSQS

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  @Qualifier("indexqueue-sqs-dlq-client")
  lateinit var indexQueueSqsDlqClient: AmazonSQS

  @Autowired
  lateinit var gson: Gson

  @Autowired
  lateinit var elasticSearchClient: RestHighLevelClient

  @Autowired
  lateinit var elasticsearchOperations: ElasticsearchOperations

  @SpyBean
  lateinit var indexQueueService: IndexQueueService

  @SpyBean
  lateinit var indexProperties: IndexProperties

  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = eventQueueSqsClient.getQueueAttributes(eventQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int? {
    val queueAttributes = eventQueueSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
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
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090AA") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090AB") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090AC") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090AD") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090AE") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090AF") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090BA") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090BB") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090BC") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090BD") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090BE") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7090BF") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A9999AA") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A9999AB") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A9999RA") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A9999RB") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A9999RC") } matches { it == 1 }

    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
    Thread.sleep(500)
  }

  fun setupIndexes() {
    createIndexStatusIndex()
    createPrisonerIndex(SyncIndex.INDEX_A)
    createPrisonerIndex(SyncIndex.INDEX_B)
  }

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
    webTestClient.post().uri("/prisoner-search/match-prisoners")
      .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun singlePrisonSearch(prisonSearch: PrisonSearch, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/match")
      .body(BodyInserters.fromValue(gson.toJson(prisonSearch)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun globalSearch(globalSearchCriteria: GlobalSearchCriteria, fileAssert: String) {
    webTestClient.post().uri("/global-search")
      .body(BodyInserters.fromValue(gson.toJson(globalSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun keywordSearch(
    keywordRequest: KeywordRequest,
    expectedCount: Int = 0,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response = webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(keywordRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(RestResponsePage::class.java)
      .returnResult().responseBody

    assertThat(response.numberOfElements).isEqualTo(expectedCount)
    assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    assertThat(response.content).extracting("prisonerNumber").containsAll(expectedPrisoners)
  }

  fun detailSearch(
    detailRequest: PrisonerDetailRequest,
    expectedCount: Int = 0,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response = webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(detailRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(RestResponsePage::class.java)
      .returnResult().responseBody

    assertThat(response.numberOfElements).isEqualTo(expectedCount)
    assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    assertThat(response.content).extracting("prisonerNumber").containsAll(expectedPrisoners)
  }

  fun prisonerMatch(matchRequest: MatchRequest, fileAssert: String) {
    webTestClient.post().uri("/match-prisoners")
      .body(BodyInserters.fromValue(gson.toJson(matchRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun globalSearchPagination(globalSearchCriteria: GlobalSearchCriteria, size: Long, page: Long, fileAssert: String) {
    webTestClient.post().uri("/global-search?size=$size&page=$page")
      .body(BodyInserters.fromValue(gson.toJson(globalSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun restrictedPatientSearch(restrictedPatientSearchCriteria: RestrictedPatientSearchCriteria, fileAssert: String) {
    webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
      .body(BodyInserters.fromValue(gson.toJson(restrictedPatientSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun restrictedPatientSearchPagination(restrictedPatientSearchCriteria: RestrictedPatientSearchCriteria, size: Long, page: Long, fileAssert: String) {
    webTestClient.post().uri("/restricted-patient-search/match-restricted-patients?size=$size&page=$page")
      .body(BodyInserters.fromValue(gson.toJson(restrictedPatientSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun prisonSearch(prisonId: String, fileAssert: String, includeRestrictedPatients: Boolean = false) {
    webTestClient.get().uri("/prisoner-search/prison/$prisonId?include-restricted-patients=$includeRestrictedPatients")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun prisonSearchPagination(prisonId: String, size: Long, page: Long, fileAssert: String) {
    webTestClient.get().uri("/prisoner-search/prison/$prisonId?size=$size&page=$page")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }
}

private fun String.readResourceAsText(): String = QueueIntegrationTest::class.java.getResource(this).readText()
