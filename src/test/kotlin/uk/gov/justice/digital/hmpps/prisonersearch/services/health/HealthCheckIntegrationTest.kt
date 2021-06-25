package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest

@ExtendWith(SpringExtension::class)
class HealthCheckIntegrationTest : IntegrationTest() {

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

  @Test
  fun `Health page reports ok`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
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
  fun `Events queue health reports UP`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.eventsQueue-health.details.queueName").isEqualTo(queueName)
      .jsonPath("components.eventsQueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.eventsQueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.eventsQueue-health.details.dlqName").isEqualTo(dlqName)
      .jsonPath("components.eventsQueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.eventsQueue-health.details.messagesOnDlq").isEqualTo(0)
  }

  @Test
  fun `Index queue health reports UP`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.indexQueue-health.details.queueName").isEqualTo(indexQueueName)
      .jsonPath("components.indexQueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.indexQueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.indexQueue-health.details.dlqName").isEqualTo(indexDlqName)
      .jsonPath("components.indexQueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.indexQueue-health.details.messagesOnDlq").isEqualTo(0)
  }

  @Test
  fun `Health liveness page is accessible`() {
    webTestClient.get().uri("/health/liveness")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health readiness page is accessible`() {
    webTestClient.get().uri("/health/readiness")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("status").isEqualTo("UP")
  }
}
