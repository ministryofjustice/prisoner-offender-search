package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest

class PrisonerSearchResourceTest : QueueIntegrationTest() {

  @Test
  fun `access forbidden when no authority`() {

    webTestClient.get().uri("/prisoner-search/match/smith")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.get().uri("/prisoner-search/match/smith")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `can perform a match on a keyword for a prisoner search`() {
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

    webTestClient.get().uri("/prisoner-search/match/smith?page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .json("{\"content\":[{\"prisonerId\":\"A7089EY\",\"bookingId\":1900836,\"bookingNo\":\"38339B\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"dateOfBirth\":\"1980-12-31\",\"agencyId\":\"MDI\",\"active\":false},{\"prisonerId\":\"A7089EZ\",\"bookingId\":1900837,\"bookingNo\":\"38339C\",\"firstName\":\"John\",\"lastName\":\"Smyth\",\"dateOfBirth\":\"1981-01-01\",\"agencyId\":\"LEI\",\"active\":false}],\"pageable\":{\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},\"offset\":0,\"pageSize\":10,\"pageNumber\":0,\"paged\":true,\"unpaged\":false},\"facets\":[],\"maxScore\":970.406,\"totalElements\":2,\"totalPages\":1,\"size\":10,\"numberOfElements\":2,\"number\":0,\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},\"first\":true,\"last\":true,\"empty\":false}")

    webTestClient.get().uri("/prisoner-search/find-by/id/A7089EY")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .json("{\"prisonerId\":\"A7089EY\",\"bookingId\":1900836,\"bookingNo\":\"38339B\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"dateOfBirth\":\"1980-12-31\",\"agencyId\":\"MDI\",\"active\":false}")

    webTestClient.get().uri("/prisoner-search/find-by/date-of-birth/1980-12-31?page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .json("{\"content\":[{\"prisonerId\":\"A7089EY\",\"bookingId\":1900836,\"bookingNo\":\"38339B\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"dateOfBirth\":\"1980-12-31\",\"agencyId\":\"MDI\",\"active\":false}],\"pageable\":{\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},\"offset\":0,\"pageNumber\":0,\"pageSize\":10,\"paged\":true,\"unpaged\":false},\"facets\":[],\"maxScore\":1.0,\"totalElements\":1,\"totalPages\":1,\"size\":10,\"numberOfElements\":1,\"number\":0,\"first\":true,\"sort\":{\"sorted\":false,\"unsorted\":true,\"empty\":true},\"last\":true,\"empty\":false}")

  }

}
