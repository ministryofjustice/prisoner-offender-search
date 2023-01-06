package uk.gov.justice.digital.hmpps.prisonersearch.integration

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.AmazonSQS
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.google.gson.Gson
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.IncentivesMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.OAuthMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.PrisonMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.RestrictedPatientMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerDifferenceService
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerEventHashRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueFactory
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.HmppsTopicFactory
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.MissingTopicException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTest.SqsConfig::class)
@ActiveProfiles(profiles = ["test"])
abstract class IntegrationTest {

  @SpyBean
  @Qualifier("eventqueue-sqs-client")
  internal lateinit var eventQueueSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("hmppsdomainqueue-sqs-client")
  internal lateinit var hmppsDomainQueueSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("hmppseventtopic-sns-client")
  internal lateinit var hmppsEventTopicSnsClient: AmazonSNS

  @SpyBean
  lateinit var hmppsQueueService: HmppsQueueService

  @SpyBean
  lateinit var clock: Clock

  protected val eventQueue by lazy { hmppsQueueService.findByQueueId("eventqueue") ?: throw MissingQueueException("HmppsQueue eventqueue not found") }
  protected val hmppsDomainQueue by lazy { hmppsQueueService.findByQueueId("hmppsdomainqueue") ?: throw MissingQueueException("HmppsQueue hmppsdomainqueue not found") }
  protected val indexQueue by lazy { hmppsQueueService.findByQueueId("indexqueue") ?: throw MissingQueueException("HmppsQueue indexqueue not found") }

  val eventQueueName by lazy { eventQueue.queueName }
  val eventQueueUrl by lazy { eventQueue.queueUrl }
  val eventDlqName by lazy { eventQueue.dlqName as String }
  val eventDlqUrl by lazy { eventQueue.dlqUrl as String }
  val indexQueueName by lazy { indexQueue.queueName }
  val indexQueueUrl by lazy { indexQueue.queueUrl }
  val indexDlqName by lazy { indexQueue.dlqName as String }
  val indexDlqUrl by lazy { indexQueue.dlqUrl as String }
  val hmppsDomainQueueName by lazy { hmppsDomainQueue.queueName }
  val hmppsDomainQueueUrl by lazy { hmppsDomainQueue.queueUrl }
  val hmppsDomainQueueDlqName by lazy { hmppsDomainQueue.dlqName as String }
  val hmppsDomainQueueDlqUrl by lazy { hmppsDomainQueue.dlqUrl as String }

  @Autowired
  private lateinit var gson: Gson

  @Autowired
  internal lateinit var webTestClient: WebTestClient

  @Autowired
  internal lateinit var jwtHelper: JwtAuthHelper

  @SpyBean
  protected lateinit var hmppsDomainEventEmitter: HmppsDomainEventEmitter

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  @Autowired
  protected lateinit var prisonerIndexService: PrisonerIndexService

  @SpyBean
  protected lateinit var prisonerDifferenceService: PrisonerDifferenceService

  @Autowired
  protected lateinit var prisonerEventHashRepository: PrisonerEventHashRepository

  companion object {
    internal val prisonMockServer = PrisonMockServer()
    internal val oauthMockServer = OAuthMockServer()
    internal val restrictedPatientMockServer = RestrictedPatientMockServer()
    internal val incentivesMockServer = IncentivesMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      prisonMockServer.start()
      oauthMockServer.start()
      restrictedPatientMockServer.start()
      incentivesMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      prisonMockServer.stop()
      oauthMockServer.stop()
      restrictedPatientMockServer.stop()
      incentivesMockServer.stop()
    }
  }

  init {
    SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "pw")
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  @BeforeEach
  fun resetStubs() {
    prisonMockServer.resetAll()
    oauthMockServer.resetAll()
    oauthMockServer.stubGrantToken()
    restrictedPatientMockServer.resetAll()
    incentivesMockServer.resetAll()
  }

  @BeforeEach
  fun mockClock() {
    val fixedClock = Clock.fixed(Instant.parse("2022-09-16T10:40:34Z"), ZoneId.of("UTC"))
    whenever(clock.instant()).thenReturn(fixedClock.instant())
    whenever(clock.zone).thenReturn(fixedClock.zone)
  }

  @BeforeEach
  fun clearPrisonerHashes() {
    prisonerEventHashRepository.deleteAll()
  }

  internal fun Any.asJson() = gson.toJson(this)

  internal fun setAuthorisation(user: String = "prisoner-search-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit {
    val token = jwtHelper.createJwt(
      subject = user,
      scope = listOf("read"),
      expiryTime = Duration.ofHours(1L),
      roles = roles
    )
    return { it.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
  }

  protected fun subPing(status: Int) {
    oauthMockServer.stubFor(
      WireMock.get("/auth/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    prisonMockServer.stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    restrictedPatientMockServer.stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
    incentivesMockServer.stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  @TestConfiguration
  class SqsConfig(private val hmppsQueueFactory: HmppsQueueFactory, private val hmppsTopicFactory: HmppsTopicFactory) {

    @Bean("eventqueue-sqs-client")
    fun eventQueueSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
      @Qualifier("eventqueue-sqs-dlq-client") eventQueueSqsDlqClient: AmazonSQS
    ): AmazonSQS =
      with(hmppsSqsProperties) {
        val config = queues["eventqueue"]
          ?: throw MissingQueueException("HmppsSqsProperties config for eventqueue not found")
        hmppsQueueFactory.createSqsClient("eventqueue", config, hmppsSqsProperties, eventQueueSqsDlqClient)
      }
    @Bean("hmppsdomainqueue-sqs-client")
    fun hmppsDomainQueueSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
      @Qualifier("hmppsdomainqueue-sqs-dlq-client") hmppsDomainQueueSqsDlqClient: AmazonSQS
    ): AmazonSQS =
      with(hmppsSqsProperties) {
        val config = queues["hmppsdomainqueue"]
          ?: throw MissingQueueException("HmppsSqsProperties config for hmppsdomainqueue not found")
        hmppsQueueFactory.createSqsClient("hmppsdomainqueue", config, hmppsSqsProperties, hmppsDomainQueueSqsDlqClient)
      }

    @Bean("hmppseventtopic-sns-client")
    fun eventQueueSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
    ): AmazonSNS =
      with(hmppsSqsProperties) {
        val config = topics["hmppseventtopic"]
          ?: throw MissingTopicException("HmppsSqsProperties config for hmppseventtopic not found")
        hmppsTopicFactory.createSnsClient("hmppseventtopic", config, hmppsSqsProperties,)
      }
  }
}
