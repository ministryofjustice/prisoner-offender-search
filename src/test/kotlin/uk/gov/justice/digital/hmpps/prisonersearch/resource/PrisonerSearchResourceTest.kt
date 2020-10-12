package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonSearch

class PrisonerSearchResourceTest : QueueIntegrationTest() {

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

    webTestClient.post().uri("/prisoner-search/match")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.post().uri("/prisoner-search/match")
      .body(BodyInserters.fromValue(gson.toJson(PrisonSearch("A7089EY", "john", "smith", "MDI"))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no criteria provided`() {

    webTestClient.post().uri("/prisoner-search/match")
      .body(BodyInserters.fromValue(gson.toJson(PrisonSearch(null, null, null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `search by prisonId access forbidden when no authority`() {

    webTestClient.get().uri("/prisoner-search/prison/MDI")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `search by prisonId access forbidden when no role`() {

    webTestClient.get().uri("/prisoner-search/prison/MDI")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `search by prisonId not found when no criteria provided`() {

    webTestClient.get().uri("/prisoner-search/prison/")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isNotFound
  }


  @Test
  fun `can perform a match on prisoner number`() {
    search(PrisonSearch("A7089EY", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match wrong prisoner number but correct name`() {
    search(PrisonSearch("X7089EY", "JOHN", "SMITH"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on PNC number`() {
    search(PrisonSearch("12/394773H", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on CRO number`() {
    search(PrisonSearch("29906/12J", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on book number`() {
    search(PrisonSearch("V61585", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on booking Id`() {
    search(PrisonSearch("1900836", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can not match when name is mis-spelt`() {
    search(PrisonSearch(null, "jon", "smith"), "/results/empty.json")
  }

  @Test
  fun `can not match when name is mis-spelt and wrong prison`() {
    search(PrisonSearch(null, "jon", "smith", "LEI"), "/results/empty.json")
  }

  @Test
  fun `can not match when name of prisoner does not exist`() {
    search(PrisonSearch(null, "trevor", "willis"), "/results/empty.json")
  }

  @Test
  fun `can perform a match on a first name only`() {
    search(PrisonSearch(null, "john", null), "/results/search_results_john.json")
  }

  @Test
  fun `can perform a match on a first and last name only`() {
    search(PrisonSearch(null, "david", "doe"), "/results/search_results_david.json")
  }

  @Test
  fun `can perform a match on a first name only filter by prison`() {
    search(PrisonSearch(null, "john", null, "MDI"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on a last name only`() {
    search(PrisonSearch(null, null, "smith"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only`() {
    search(PrisonSearch(null, "john", "smith"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only in specific prison`() {
    search(PrisonSearch(null, "john", "smith", "MDI"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only in wrong prison`() {
    search(PrisonSearch(null, "john", "smith", "LEI"), "/results/empty.json")
  }

  @Test
  fun `can perform a match on first and last name in alias`() {
    search(PrisonSearch(null, "master", "cordian", null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but they must be from the same record`() {
    search(PrisonSearch(null, "master", "stark", null, true), "/results/empty.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but they must be from the same record matches`() {
    search(PrisonSearch(null, "tony", "stark", null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on firstname only in alias`() {
    search(PrisonSearch(null, "master", null, null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on last name only in alias`() {
    search(PrisonSearch(null, null, "cordian", null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but with alias search off`() {
    search(PrisonSearch(null, "master", "cordian", null, false), "/results/empty.json")
  }

  @Test
  fun `can perform a match on firstname only in alias but with alias search off`() {
    search(PrisonSearch(null, "master", null, null, false), "/results/empty.json")
  }

  @Test
  fun `can perform a match on last name only in alias but with alias search off`() {
    search(PrisonSearch(null, null, "cordian", null, false), "/results/empty.json")
  }

  @Test
  fun `can perform a match on a last name and specific prison`() {
    search(PrisonSearch(null, null, "smyth", "LEI"), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match which returns no results as not in that prison`() {
    search(PrisonSearch(null, null, "smyth", "MDI"), "/results/empty.json")
  }

  @Test
  fun `can perform a which returns result for ID search as in correct prison`() {
    search(PrisonSearch("A7089EY", null, null, "MDI"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a which returns no results as not in that prison by id`() {
    search(PrisonSearch("A7089EY", null, null, "LEI"), "/results/empty.json")
  }

  @Test
  fun `can perform a match on prisonId`() {
    prisonSearch( "LNI", "/results/search_results_lni.json")
  }

  @Test
  fun `can perform a match on lowercase prisonId`() {
    prisonSearch( "lni", "/results/search_results_lni.json")
  }

  @Test
  fun `can perform a match on prisonId returns 1 result from second page`() {
    prisonSearchPagination("MDI", 1, 1, "/results/search_results_mdi_pagination1.json")
  }

  @Test
  fun `can perform a match on prisonId returns 2 result from third page`() {
    prisonSearchPagination( "MDI",2,2, "/results/search_results_mdi_pagination2.json")
  }

  @Test
  fun `can perform a match on prisonId OUT`() {
    prisonSearch( "OUT", "/results/search_results_out.json")
  }
}



