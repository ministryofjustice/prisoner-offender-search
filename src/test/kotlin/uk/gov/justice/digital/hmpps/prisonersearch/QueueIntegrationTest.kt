package uk.gov.justice.digital.hmpps.prisonersearch

import com.amazonaws.services.sqs.AmazonSQS
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestHighLevelClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
import uk.gov.justice.digital.hmpps.prisonersearch.resource.PrisonerSearchByPrisonerNumbersResourceTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexQueueService
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonSearch
import uk.gov.justice.digital.hmpps.prisonersearch.services.ReleaseDateSearch
import uk.gov.justice.digital.hmpps.prisonersearch.services.RestrictedPatientSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PossibleMatchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerDetailRequest
import uk.gov.justice.hmpps.sqs.MissingQueueException
import java.time.Duration
import java.time.LocalDate
import kotlin.random.Random

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
  lateinit var elasticsearchClient: RestHighLevelClient

  @Autowired
  lateinit var elasticsearchOperations: ElasticsearchOperations

  @SpyBean
  lateinit var indexQueueService: IndexQueueService

  @SpyBean
  lateinit var indexProperties: IndexProperties

  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  protected val hmppsEventsQueue by lazy { hmppsQueueService.findByQueueId("hmppseventtestqueue") ?: throw MissingQueueException("hmppseventtestqueue queue not found") }

  fun getNumberOfMessagesCurrentlyOnQueue(): Int {
    val queueAttributes = eventQueueSqsClient.getQueueAttributes(eventQueueUrl, listOf("ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"))
    val visible = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()!!
    val notVisible = queueAttributes.attributes["ApproximateNumberOfMessagesNotVisible"]?.toInt()!!
    val number = visible + notVisible
    log.trace("Messages on event queue: visible = {} notVisible = {} ", visible, notVisible)
    return number
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int {
    val queueAttributes = eventQueueSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"))
    val visible = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()!!
    val notVisible = queueAttributes.attributes["ApproximateNumberOfMessagesNotVisible"]?.toInt()!!
    val number = visible + notVisible
    log.trace("Messages on index queue: visible = {} notVisible = {} ", visible, notVisible)
    return number
  }

  fun getNumberOfMessagesCurrentlyOnDomainQueue(): Int {
    val queueAttributes = hmppsEventsQueue.sqsClient.getQueueAttributes(hmppsEventsQueue.queueUrl, listOf("ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"))
    val visible = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()!!
    val notVisible = queueAttributes.attributes["ApproximateNumberOfMessagesNotVisible"]?.toInt()!!
    val number = visible + notVisible
    log.trace("Messages on domain queue: visible = {} notVisible = {} ", visible, notVisible)
    return number
  }

  fun prisonRequestCountFor(url: String): Int {
    val count = prisonMockServer.findAll(getRequestedFor(urlEqualTo(url))).count()
    log.trace("Count for {}: {}", url, count)
    return count
  }

  fun indexPrisoners() {
    webTestClient.put().uri("/prisoner-index/build-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it!! > 0 }
    await.atMost(Duration.ofSeconds(60)) untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
  }

  fun setupIndexes() {
    createIndexStatusIndex()
    createPrisonerIndex(SyncIndex.INDEX_A)
    createPrisonerIndex(SyncIndex.INDEX_B)
  }

  fun loadPrisoners(prisoner: List<PrisonerBuilder>) {
    setupIndexes()
    val prisonerNumbers = prisoner.map { it.prisonerNumber }.toList()
    prisonMockServer.stubFor(
      WireMock.get(urlEqualTo("/api/offenders/ids"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withHeader("Total-Records", prisonerNumbers.size.toString())
            .withBody(gson.toJson(prisonerNumbers.map { PrisonerSearchByPrisonerNumbersResourceTest.IDs(it) }))
        )
    )
    prisoner.forEach {
      prisonMockServer.stubFor(
        WireMock.get(urlEqualTo("/api/offenders/${it.prisonerNumber}"))
          .willReturn(
            WireMock.aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(it.toOffenderBooking())
          )
      )
    }

    webTestClient.put().uri("/prisoner-index/build-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    await.atMost(Duration.ofSeconds(60)) untilCallTo { prisonRequestCountFor("/api/offenders/${prisonerNumbers.last()}") } matches { it == 1 }
    await.atMost(Duration.ofSeconds(60)) untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  private fun createIndexStatusIndex() {
    val response = elasticsearchClient.lowLevelClient.performRequest(Request("HEAD", "/offender-index-status"))
    if (response.statusLine.statusCode == 404) {
      val indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of("offender-index-status"))
      indexOperations.create()
      indexOperations.putMapping(indexOperations.createMapping(IndexStatus::class.java))
    }
    val resetIndexStatus = Request("PUT", "/offender-index-status/_doc/STATUS")
    resetIndexStatus.setJsonEntity(gson.toJson(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false)))
    elasticsearchClient.lowLevelClient.performRequest(resetIndexStatus)
  }

  private fun createPrisonerIndex(prisonerIndex: SyncIndex) {
    val response = elasticsearchClient.lowLevelClient.performRequest(Request("HEAD", "/${prisonerIndex.indexName}"))
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

  fun getPrisoner(id: String, fileAssert: String) {
    webTestClient.get().uri("/prisoner/$id")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
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

  fun possibleMatch(matchRequest: PossibleMatchCriteria, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/possible-matches")
      .body(BodyInserters.fromValue(gson.toJson(matchRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText(), true)
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

  fun restrictedPatientSearchPagination(
    restrictedPatientSearchCriteria: RestrictedPatientSearchCriteria,
    size: Long,
    page: Long,
    fileAssert: String
  ) {
    webTestClient.post().uri("/restricted-patient-search/match-restricted-patients?size=$size&page=$page")
      .body(BodyInserters.fromValue(gson.toJson(restrictedPatientSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun searchByReleaseDate(searchCriteria: ReleaseDateSearch, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun searchByReleaseDatePagination(searchCriteria: ReleaseDateSearch, size: Long, page: Long, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison?size=$size&page=$page")
      .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
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

  protected fun getOffenderBookingTemplate(): OffenderBooking {
    return gson.fromJson("/templates/booking.json".readResourceAsText(), OffenderBooking::class.java)
  }

  fun PrisonerBuilder.toOffenderBooking(): String {
    return gson.toJson(
      getOffenderBookingTemplate().copy(
        offenderNo = this.prisonerNumber,
        firstName = this.firstName,
        lastName = this.lastName,
        agencyId = this.agencyId,
        dateOfBirth = LocalDate.parse(this.dateOfBirth),
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = this.agencyId,
          locationId = Random.nextLong(),
          description = this.cellLocation,
          agencyName = "$agencyId (HMP)"
        ),
        alerts = this.alertCodes.map { (type, code) ->
          Alert(
            alertId = Random.nextLong(),
            offenderNo = this.prisonerNumber,
            alertCode = code,
            alertCodeDescription = "Code description for $code",
            alertType = type,
            alertTypeDescription = "Type Description for $type",
            expired = false, // In search all alerts are not expired and active
            active = true,
            dateCreated = LocalDate.now(),
          )
        },
      ).let {
        if (released) {
          it.copy(
            status = "INACTIVE OUT",
            lastMovementTypeCode = "REL",
            lastMovementReasonCode = "HP",
            inOutStatus = "OUT",
            agencyId = "OUT",
          )
        } else {
          it
        }
      }
    )
  }
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class PrisonerBuilder(
  val prisonerNumber: String = generatePrisonerNumber(),
  val firstName: String = "LUCAS",
  val lastName: String = "MORALES",
  val agencyId: String = "MDI",
  val released: Boolean = false,
  val alertCodes: List<Pair<String, String>> = listOf(),
  val dateOfBirth: String = "1965-07-19",
  val cellLocation: String = "A-1-1",
)

fun String.readResourceAsText(): String = QueueIntegrationTest::class.java.getResource(this).readText()

fun generatePrisonerNumber(): String {
  // generate random string starting with a letter, followed by 4 numbers and 2 letters
  return "${letters(1)}${numbers(4)}${letters(2)}"
}

fun letters(length: Int): String {
  return RandomStringUtils.random(length, true, true)
}

fun numbers(length: Int): String {
  return RandomStringUtils.random(length, false, true)
}
