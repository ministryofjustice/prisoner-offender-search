package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Ignore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest


@Ignore  // Localstack mis-behaving at the moment with Retries Policy failing
@ExtendWith(SpringExtension::class)
class HealthCheckIntegrationTest : IntegrationTest() {
  @Autowired
  private lateinit var queueHealth: QueueHealth

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
    ReflectionTestUtils.setField(queueHealth, "queueName", queueName)
    ReflectionTestUtils.setField(queueHealth, "dlqName", dlqName)
    ReflectionTestUtils.setField(indexQueueHealth, "indexQueueName", indexQueueName)
    ReflectionTestUtils.setField(indexQueueHealth, "indexDlqName", indexDlqName)
  }

  @Test
  fun `Health page reports ok`() {
    subPing(200)

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("components.oauthApiHealth.details.HttpStatus").isEqualTo("OK")
        .jsonPath("components.nomisApiHealth.details.HttpStatus").isEqualTo("OK")
        .jsonPath("status").isEqualTo("UP")
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
    ReflectionTestUtils.setField(queueHealth, "queueName", "missing_queue")
    subPing(200)

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .is5xxServerError
        .expectBody()
        .jsonPath("components.queueHealth.status").isEqualTo("DOWN")
        .jsonPath("status").isEqualTo("DOWN")

  }

  @Test
  fun `Queue health ok and dlq health ok, reports everything up`() {
    subPing(200)

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("components.queueHealth.status").isEqualTo("UP")
        .jsonPath("components.queueHealth.status").isEqualTo(DlqStatus.UP.description)
        .jsonPath("status").isEqualTo("UP")

  }

  @Test
  fun `Dlq health reports interesting attributes`() {
    subPing(200)

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("components.queueHealth.details.${QueueAttributes.MESSAGES_ON_DLQ.healthName}").isEqualTo(0)

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
        .jsonPath("components.queueHealth.status").isEqualTo("DOWN")
        .jsonPath("components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
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
        .jsonPath("components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)

  }

  @Test
  fun `Dlq not found reports dlq down`() {
    subPing(200)
    ReflectionTestUtils.setField(queueHealth, "dlqName", "missing_queue")

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .is5xxServerError
        .expectBody()
        .jsonPath("components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)

  }

  private fun subPing(status: Int) {
    oauthMockServer.stubFor(get("/auth/health/ping").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(if (status == 200) "pong" else "some error")
        .withStatus(status)))

    prisonMockServer.stubFor(get("/health/ping").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(if (status == 200) "pong" else "some error")
        .withStatus(status)))
  }

  private fun mockQueueWithoutRedrivePolicyAttributes() {
    val queueName = ReflectionTestUtils.getField(queueHealth, "queueName") as String
    val queueUrl = awsSqsClient.getQueueUrl(queueName)
    whenever(awsSqsClient.getQueueAttributes(GetQueueAttributesRequest(queueUrl.queueUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))))
        .thenReturn(GetQueueAttributesResult())
  }
}
