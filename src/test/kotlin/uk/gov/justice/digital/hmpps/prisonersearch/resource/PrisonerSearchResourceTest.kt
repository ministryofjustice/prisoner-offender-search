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
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089EY") } matches { it == 1 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089EZ") } matches { it == 1 }

    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get().uri("/prisoner-search/match/smith?page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/search_results_smith.json".readResourceAsText())

    webTestClient.get().uri("/prisoner-search/match/1900836?page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/search_results_smith.json".readResourceAsText())

    webTestClient.get().uri("/prisoner-search/match/A7089EY?page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/search_results_smith.json".readResourceAsText())

    webTestClient.get().uri("/prisoner-search/match/smyth?prisonId=LEI&page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/search_results_smyth.json".readResourceAsText())

    webTestClient.get().uri("/prisoner-search/match/smyth?prisonId=MDI&page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/empty.json".readResourceAsText())

    webTestClient.get().uri("/prisoner-search/match/cordian")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/search_results_smyth.json".readResourceAsText())

    webTestClient.get().uri("/prisoner-search/find-by/id/A7089EY")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/search_result_A7089EY.json".readResourceAsText())

    webTestClient.get().uri("/prisoner-search/find-by/date-of-birth/1980-12-31?page=0&size=10")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/search_results_dob.json".readResourceAsText())
  }
}

private fun String.readResourceAsText(): String = PrisonerSearchResourceTest::class.java.getResource(this).readText()
