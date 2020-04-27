package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest

class PrisonerIndexResourceTest : IntegrationTest() {

  @Test
  fun `access forbidden when no authority`() {

    webTestClient.get().uri("/prisoner-index/build-index")
        .exchange()
        .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.get().uri("/prisoner-index/build-index")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden
  }

  @Test
  fun `can index a prison with correct role`() {
    webTestClient.get().uri("/prisoner-index/build-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
  }
}
