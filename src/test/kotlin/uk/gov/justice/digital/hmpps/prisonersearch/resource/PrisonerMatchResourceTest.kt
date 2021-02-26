package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchRequest

class PrisonerMatchResourceTest : QueueIntegrationTest() {

  companion object {
    var initialiseSearchData = true
  }

  @BeforeEach
  fun setup() {

    if (initialiseSearchData) {

      setupIndexes()
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      initialiseSearchData = false
    }
  }

  @Test
  fun `access forbidden when no authority`() {

    webTestClient.post().uri("/match-prisoners")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.post().uri("/match-prisoners")
      .body(BodyInserters.fromValue(gson.toJson(MatchRequest("john", "smith", null, null, null, "A7089EY"))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no criteria provided`() {

    webTestClient.post().uri("/match-prisoners")
      .body(BodyInserters.fromValue(gson.toJson(MatchRequest(null, null, null, null, null, null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a match on prisoner number`() {
    prisonerMatch(
      MatchRequest(null, null, null, null, null, "A7089EY"),
      "/results/prisonerMatch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a match on PNC number`() {
    prisonerMatch(
      MatchRequest(null, null, null, "12/394773H", null, null),
      "/results/prisonerMatch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a match on PNC number short year 19 century`() {
    prisonerMatch(
      MatchRequest(null, null, null, null, "89/4444S", null),
      "/results/prisonerMatch/search_results_pnc2.json"
    )
  }

  @Test
  fun `can perform a match on PNC number long year 19 century`() {
    prisonerMatch(
      MatchRequest(null, null, null, "1989/4444S", null, null),
      "/results/prisonerMatch/search_results_pnc2.json"
    )
  }

  @Test
  fun `can perform a match on PNC number long year 19 century extra zeros`() {
    prisonerMatch(
      MatchRequest(null, null, null, null, "1989/0004444S", null),
      "/results/prisonerMatch/search_results_pnc2.json"
    )
  }

  @Test
  fun `can perform a match on CRO number`() {
    prisonerMatch(
      MatchRequest(null, null, null, null, "29906/12J", null),
      "/results/prisonerMatch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a match on a last name only`() {
    prisonerMatch(
      MatchRequest(null, "smith", null, null, null, null),
      "/results/prisonerMatch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a match on first and last name only single hit`() {
    prisonerMatch(
      MatchRequest("john", "smith", null, null, null, null),
      "/results/prisonerMatch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a match on a first and last name only multiple hits include aliases`() {
    prisonerMatch(
      MatchRequest("sam", "jones", null, null, null, null),
      "/results/prisonerMatch/search_results_sams_aliases.json"
    )
  }
}
