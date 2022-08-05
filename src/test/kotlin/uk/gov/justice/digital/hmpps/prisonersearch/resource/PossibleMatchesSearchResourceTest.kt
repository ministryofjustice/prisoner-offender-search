package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PossibleMatchCriteria
import java.time.LocalDate

class PossibleMatchesSearchResourceTest : QueueIntegrationTest() {

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
  fun `search for possible matches access forbidden when no authority`() {

    webTestClient.post().uri("/prisoner-search/possible-matches")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `search for possible matches access forbidden when no role`() {

    webTestClient.post().uri("/prisoner-search/possible-matches")
      .body(BodyInserters.fromValue(gson.toJson(PossibleMatchCriteria(null, null, null, null, "A1234AB"))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `search for possible matches bad request when no criteria provided`() {

    webTestClient.post().uri("/prisoner-search/possible-matches")
      .body(BodyInserters.fromValue(gson.toJson(PossibleMatchCriteria(null, null, null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can search for possible matches by noms number - get one result`() {
    possibleMatch((PossibleMatchCriteria(null, null, null, null, "A7089FA")), "/results/possibleMatches/search_results_A7089FA.json")
  }

  @Test
  fun `can search for possible matches by noms number - get no results`() {
    possibleMatch((PossibleMatchCriteria(null, null, null, null, "A6759ZZ")), "/results/possibleMatches/empty.json")
  }

  @Test
  fun `can search for possible matches by noms number - when case insensitive`() {
    possibleMatch((PossibleMatchCriteria(null, null, null, null, "a7089fa")), "/results/possibleMatches/search_results_A7089FA.json")
  }

  @Test
  fun `can search for possible matches by pnc number`() {
    possibleMatch((PossibleMatchCriteria(null, null, null, "2015/001234S", null)), "/results/possibleMatches/search_results_pnc.json")
  }

  @Test
  fun `can search for possible matches by pnc number - when case insensitive`() {
    possibleMatch((PossibleMatchCriteria(null, null, null, "2015/001234s", null)), "/results/possibleMatches/search_results_pnc.json")
  }

  @Test
  fun `can search for possible matches by last name and date of birth - get one result`() {
    possibleMatch((PossibleMatchCriteria(null, "Davies", LocalDate.of(1990, 1, 31), null, null)), "/results/possibleMatches/search_results_A7089FA.json")
  }

  @Test
  fun `can search for possible matches by last name and date of birth - get no results`() {
    possibleMatch((PossibleMatchCriteria(null, "Smith", LocalDate.of(1990, 1, 31), null, null)), "/results/possibleMatches/empty.json")
  }

  @Test
  fun `can search for possible matches with all search criteria - get one result`() {
    possibleMatch((PossibleMatchCriteria("Paul", "Booth", LocalDate.of(1976, 3, 1), "2015/001234S", "A9999AA")), "/results/possibleMatches/multiple_criteria_single_match.json")
  }

  @Test
  fun `can search for possible matches with all search criteria - get multiple results`() {
    possibleMatch((PossibleMatchCriteria("James", "Davies", LocalDate.of(1990, 1, 31), "2015/001234S", "A7089FB")), "/results/possibleMatches/multiple_results.json")
  }
}
