package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest

class PrisonerIndexResourceTest : QueueIntegrationTest() {

  @Test
  fun `access forbidden when no authority`() {

    webTestClient.put().uri("/prisoner-index/build-index")
        .exchange()
        .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.put().uri("/prisoner-index/build-index")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden
  }

  @Test
  fun `can index a prison with correct role`() {
    webTestClient.put().uri("/prisoner-index/build-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

    await untilCallTo { prisonRequestCountFor("/api/offenders/ids") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/bookings/offenderNo/A7089EY") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/bookings/offenderNo/A7089EZ") } matches { it == 1 }

    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

}
