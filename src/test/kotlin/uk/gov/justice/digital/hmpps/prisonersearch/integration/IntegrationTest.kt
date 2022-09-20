package uk.gov.justice.digital.hmpps.prisonersearch.integration

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
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.OAuthMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.PrisonMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.RestrictedPatientMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.hmpps.sqs.HmppsQueueFactory
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.MissingQueueException
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
  lateinit var hmppsQueueService: HmppsQueueService

  @SpyBean
  lateinit var clock: Clock

  protected val eventQueue by lazy { hmppsQueueService.findByQueueId("eventqueue") ?: throw MissingQueueException("HmppsQueue eventqueue not found") }
  protected val indexQueue by lazy { hmppsQueueService.findByQueueId("indexqueue") ?: throw MissingQueueException("HmppsQueue indexqueue not found") }

  val eventQueueName by lazy { eventQueue.queueName }
  val eventQueueUrl by lazy { eventQueue.queueUrl }
  val eventDlqName by lazy { eventQueue.dlqName as String }
  val eventDlqUrl by lazy { eventQueue.dlqUrl as String }
  val indexQueueName by lazy { indexQueue.queueName }
  val indexQueueUrl by lazy { indexQueue.queueUrl }
  val indexDlqName by lazy { indexQueue.dlqName as String }
  val indexDlqUrl by lazy { indexQueue.dlqUrl as String }

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

  companion object {
    internal val prisonMockServer = PrisonMockServer()
    internal val oauthMockServer = OAuthMockServer()
    internal val restrictedPatientMockServer = RestrictedPatientMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      prisonMockServer.start()
      oauthMockServer.start()
      restrictedPatientMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      prisonMockServer.stop()
      oauthMockServer.stop()
      restrictedPatientMockServer.stop()
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
  }

  @BeforeEach
  fun mockClock() {
    val fixedClock = Clock.fixed(Instant.parse("2022-09-16T10:40:34Z"), ZoneId.of("UTC"))
    whenever(clock.instant()).thenReturn(fixedClock.instant())
    whenever(clock.zone).thenReturn(fixedClock.zone)
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
  }

  @TestConfiguration
  class SqsConfig(private val hmppsQueueFactory: HmppsQueueFactory) {

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
  }
}
