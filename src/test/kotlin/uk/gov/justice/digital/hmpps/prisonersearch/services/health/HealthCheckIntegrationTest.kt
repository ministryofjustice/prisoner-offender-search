package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest

@ExtendWith(SpringExtension::class)
class HealthCheckIntegrationTest : IntegrationTest() {

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
      .jsonPath("components.restrictedPatientsApiHealth.details.HttpStatus").isEqualTo("OK")
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
      .jsonPath("components.restrictedPatientsApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
      .jsonPath("components.incentivesApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
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
      .jsonPath("components.restrictedPatientsApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("components.incentivesApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
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
      .jsonPath("components.eventqueue-health.details.queueName").isEqualTo(eventQueueName)
      .jsonPath("components.eventqueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.eventqueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.eventqueue-health.details.dlqName").isEqualTo(eventDlqName)
      .jsonPath("components.eventqueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.eventqueue-health.details.messagesOnDlq").isEqualTo(0)
  }

  @Test
  fun `HMPPS Domain queue health reports UP`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.hmppsdomainqueue-health.details.queueName").isEqualTo(hmppsDomainQueueName)
      .jsonPath("components.hmppsdomainqueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.hmppsdomainqueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.hmppsdomainqueue-health.details.dlqName").isEqualTo(hmppsDomainQueueDlqName)
      .jsonPath("components.hmppsdomainqueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.hmppsdomainqueue-health.details.messagesOnDlq").isEqualTo(0)
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
      .jsonPath("components.indexqueue-health.details.queueName").isEqualTo(indexQueueName)
      .jsonPath("components.indexqueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.indexqueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.indexqueue-health.details.dlqName").isEqualTo(indexDlqName)
      .jsonPath("components.indexqueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.indexqueue-health.details.messagesOnDlq").isEqualTo(0)
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

  @Test
  fun `Database reports UP`() {
    subPing(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.db.status").isEqualTo("UP")
      .jsonPath("components.db.details.database").isEqualTo("PostgreSQL")
  }
}
