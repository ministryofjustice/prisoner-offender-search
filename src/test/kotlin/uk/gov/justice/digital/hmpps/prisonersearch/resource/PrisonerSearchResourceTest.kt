package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria

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

    webTestClient.post().uri("/prisoner-search/match-prisoners")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.post().uri("/prisoner-search/match-prisoners")
      .body(BodyInserters.fromValue(gson.toJson(SearchCriteria("A7089EY", "john", "smith", listOf("MDI")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no criteria provided`() {

    webTestClient.post().uri("/prisoner-search/match-prisoners")
      .body(BodyInserters.fromValue(gson.toJson(SearchCriteria(null, null, null))))
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
    search(SearchCriteria("A7089EY", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match wrong prisoner number but correct name`() {
    search(SearchCriteria("X7089EY", "JOHN", "SMITH"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on PNC number`() {
    search(SearchCriteria("12/394773H", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on PNC number short year`() {
    search(SearchCriteria("15/1234S", null, null), "/results/search_results_pnc.json")
  }

  @Test
  fun `can perform a match on PNC number long year`() {
    search(SearchCriteria("2015/1234S", null, null), "/results/search_results_pnc.json")
  }

  @Test
  fun `can perform a match on PNC number long year extra zeros`() {
    search(SearchCriteria("2015/001234S", null, null), "/results/search_results_pnc.json")
  }

  @Test
  fun `can perform a match on PNC number short year 19 century`() {
    search(SearchCriteria("89/4444S", null, null), "/results/search_results_pnc2.json")
  }

  @Test
  fun `can perform a match on PNC number long year 19 century`() {
    search(SearchCriteria("1989/4444S", null, null), "/results/search_results_pnc2.json")
  }

  @Test
  fun `can perform a match on PNC number long year 19 century extra zeros`() {
    search(SearchCriteria("1989/0004444S", null, null), "/results/search_results_pnc2.json")
  }

  @Test
  fun `can perform a match on CRO number`() {
    search(SearchCriteria("29906/12J", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on book number`() {
    search(SearchCriteria("V61585", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on booking Id`() {
    search(SearchCriteria("1900836", null, null), "/results/search_results_smith.json")
  }

  @Test
  fun `can not match when name is mis-spelt`() {
    search(SearchCriteria(null, "jon", "smith"), "/results/empty.json")
  }

  @Test
  fun `can not match when name is mis-spelt and wrong prison`() {
    search(SearchCriteria(null, "jon", "smith", listOf("LEI")), "/results/empty.json")
  }

  @Test
  fun `can not match when name of prisoner does not exist`() {
    search(SearchCriteria(null, "trevor", "willis"), "/results/empty.json")
  }

  @Test
  fun `can perform a match on a first name only`() {
    search(SearchCriteria(null, "john", null), "/results/search_results_john.json")
  }

  @Test
  fun `can perform a match on a first and last name only`() {
    search(SearchCriteria(null, "david", "doe"), "/results/search_results_david.json")
  }

  @Test
  fun `can perform a match where empty prison list is ignored`() {
    search(SearchCriteria(null, "john", null, emptyList()), "/results/search_results_john.json")
  }

  @Test
  fun `can perform a match on a first name only filter by single prison`() {
    search(SearchCriteria(null, "john", null, listOf("MDI")), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on a first name only filter by multiple prisons`() {
    search(SearchCriteria(null, "john", null, listOf("MDI", "LEI")), "/results/search_results_john.json")
  }

  @Test
  fun `can perform a match on a last name only`() {
    search(SearchCriteria(null, null, "smith"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only`() {
    search(SearchCriteria(null, "john", "smith"), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only in specific prison`() {
    search(SearchCriteria(null, "john", "smith", listOf("MDI")), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only in wrong prison`() {
    search(SearchCriteria(null, "john", "smith", listOf("LEI")), "/results/empty.json")
  }

  @Test
  fun `can perform a match on first and last name in alias`() {
    search(SearchCriteria(null, "master", "cordian", null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but they must be from the same record`() {
    search(SearchCriteria(null, "master", "stark", null, true), "/results/empty.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but they must be from the same record matches`() {
    search(SearchCriteria(null, "tony", "stark", null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on firstname only in alias`() {
    search(SearchCriteria(null, "master", null, null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on last name only in alias`() {
    search(SearchCriteria(null, null, "cordian", null, true), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but with alias search off`() {
    search(SearchCriteria(null, "master", "cordian", null, false), "/results/empty.json")
  }

  @Test
  fun `can perform a match on firstname only in alias but with alias search off`() {
    search(SearchCriteria(null, "master", null, null, false), "/results/empty.json")
  }

  @Test
  fun `can perform a match on last name only in alias but with alias search off`() {
    search(SearchCriteria(null, null, "cordian", null, false), "/results/empty.json")
  }

  @Test
  fun `can perform a match on a last name and specific prison`() {
    search(SearchCriteria(null, null, "smyth", listOf("LEI")), "/results/search_results_smyth.json")
  }

  @Test
  fun `can perform a match which returns no results as not in that prison`() {
    search(SearchCriteria(null, null, "smyth", listOf("MDI")), "/results/empty.json")
  }

  @Test
  fun `can perform a which returns result for ID search as in correct prison`() {
    search(SearchCriteria("A7089EY", null, null, listOf("MDI")), "/results/search_results_smith.json")
  }

  @Test
  fun `can perform a which returns no results as not in that prison by id`() {
    search(SearchCriteria("A7089EY", null, null, listOf("LEI")), "/results/empty.json")
  }

  @Test
  fun `can perform a match on prisonId`() {
    prisonSearch("LNI", "/results/search_results_lni.json")
  }

  @Test
  fun `can perform a match on lowercase prisonId`() {
    prisonSearch("lni", "/results/search_results_lni.json")
  }

  @Test
  fun `can perform a match on prisonId returns 1 result from second page`() {
    prisonSearchPagination("MDI", 1, 1, "/results/search_results_mdi_pagination1.json")
  }

  @Test
  fun `can perform a match on prisonId returns 2 result from third page`() {
    prisonSearchPagination("MDI", 2, 2, "/results/search_results_mdi_pagination2.json")
  }

  @Test
  fun `can perform a match on prisonId OUT`() {
    prisonSearch("OUT", "/results/search_results_out.json")
  }
}
