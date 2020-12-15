package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest

@ExtendWith(SpringExtension::class)
class InfoIntegrationTest : QueueIntegrationTest() {

  @BeforeEach
  fun init() {
    setupIndexes()
  }

  @Test
  fun `Info page reports ok`() {
    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.id").isEqualTo("STATUS")
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-size.INDEX_A").isNotEmpty
      .jsonPath("index-size.INDEX_B").isNotEmpty
  }
}
