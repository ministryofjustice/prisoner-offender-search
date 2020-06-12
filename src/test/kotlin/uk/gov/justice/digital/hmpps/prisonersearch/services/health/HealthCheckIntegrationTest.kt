package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest

@ExtendWith(SpringExtension::class)
class HealthCheckIntegrationTest : IntegrationTest() {
  @Autowired
  private lateinit var eventQueueHealth: EventQueueHealth

  @Autowired
  private lateinit var indexQueueHealth: IndexQueueHealth

  @Autowired
  @Value("\${sqs.queue.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var queueName: String

  @Autowired
  @Value("\${sqs.dlq.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var dlqName: String

  @Autowired
  @Value("\${sqs.index.queue.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var indexQueueName: String

  @Autowired
  @Value("\${sqs.index.dlq.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var indexDlqName: String


  @AfterEach
  fun tearDown() {
    ReflectionTestUtils.setField(eventQueueHealth, QueueHealth::class.java, "queueName", queueName, String::class.java)
    ReflectionTestUtils.setField(eventQueueHealth, QueueHealth::class.java, "dlqName", dlqName, String::class.java)
    ReflectionTestUtils.setField(indexQueueHealth, QueueHealth::class.java, "queueName", indexQueueName, String::class.java)
    ReflectionTestUtils.setField(indexQueueHealth, QueueHealth::class.java, "dlqName", indexDlqName, String::class.java)
  }

  @Test
  fun `Health ping page is accessible`() {
    subPing(200)

    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health page reports down`() {
    subPing(404)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.oauthApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
      .jsonPath("components.nomisApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
  }

  @Test
  fun `Health page reports a teapot`() {
    subPing(418)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.oauthApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("components.nomisApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Queue does not exist reports down`() {
    ReflectionTestUtils.setField(eventQueueHealth, QueueHealth::class.java, "queueName", "missing_queue", String::class.java)
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.eventQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("status").isEqualTo("DOWN")

  }

  @Test
  fun `Index Queue does not exist reports down`() {
    ReflectionTestUtils.setField(indexQueueHealth, QueueHealth::class.java, "queueName", "missing_queue", String::class.java)
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.indexQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("status").isEqualTo("DOWN")

  }

  @Test
  fun `Dlq down brings main health and queue health down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.eventQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("components.eventQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `Main queue has no redrive policy reports dlq down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.eventQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)

  }

  @Test
  fun `Dlq not found reports dlq down`() {
    subPing(200)
    ReflectionTestUtils.setField(eventQueueHealth, QueueHealth::class.java, "dlqName", "missing_queue", String::class.java)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.eventQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)

  }

  private fun subPing(status: Int) {
    oauthMockServer.stubFor(
      get("/auth/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )

    prisonMockServer.stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  private fun mockQueueWithoutRedrivePolicyAttributes() {
    val queueName = ReflectionTestUtils.getField(eventQueueHealth, QueueHealth::class.java, "queueName") as String
    val queueUrl = awsSqsClient.getQueueUrl(queueName)
    whenever(
      awsSqsClient.getQueueAttributes(
        GetQueueAttributesRequest(queueUrl.queueUrl).withAttributeNames(
          listOf(
            QueueAttributeName.All.toString()
          )
        )
      )
    )
      .thenReturn(GetQueueAttributesResult())
  }
}
