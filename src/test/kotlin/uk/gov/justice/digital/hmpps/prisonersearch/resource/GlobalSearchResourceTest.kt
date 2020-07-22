package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.Gender
import uk.gov.justice.digital.hmpps.prisonersearch.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria
import java.time.LocalDate

class GlobalSearchResourceTest : QueueIntegrationTest() {

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

    webTestClient.post().uri("/prisoner-search/global")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.post().uri("/prisoner-search/global")
      .body(BodyInserters.fromValue(gson.toJson(SearchCriteria("A7089EY", "john", "smith", "MDI"))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no criteria provided`() {

    webTestClient.post().uri("/prisoner-search/global")
      .body(BodyInserters.fromValue(gson.toJson(SearchCriteria(null, null, null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a match on prisoner number`() {
    globalSearch(GlobalSearchCriteria("A7089EY", null, null, null, null, null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match wrong prisoner number but correct name`() {
    globalSearch(GlobalSearchCriteria("X7089EY", "JOHN", "SMITH", null, null, null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match on PNC number`() {
    globalSearch(GlobalSearchCriteria("12/394773H", null, null, null, null, null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match on CRO number`() {
    globalSearch(GlobalSearchCriteria("29906/12J", null, null, null, null,null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match on book number`() {
    globalSearch(GlobalSearchCriteria("V61585", null, null, null, null,null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match on booking Id`() {
    globalSearch(GlobalSearchCriteria("1900836", null, null, null, null,null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can not match when name is mis-spelt`() {
    globalSearch(GlobalSearchCriteria(null, "jon", "smith", null, null,null), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can not match when name is mis-spelt and wrong location`() {
    globalSearch(GlobalSearchCriteria(null, "jon", "smith",  null,"OUT", null), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can not match when name of prisoner not exists`() {
    globalSearch(GlobalSearchCriteria(null, "trevor", "willis", null, null, null), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a match on a first name only`() {
    globalSearch(GlobalSearchCriteria(null, "john", null, null, null, null), "/results/globalSearch/search_results_john.json")
  }

  @Test
  fun `can perform a match on a last name only`() {
    globalSearch(GlobalSearchCriteria(null, null, "smith", null, null,null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only single hit`() {
    globalSearch(GlobalSearchCriteria(null, "john", "smith", null, null, null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match on a first and last name only multiple hits`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", null, null, null), "/results/globalSearch/search_results_sams.json")
  }
  @Test
  fun `can perform a match on a first and last name only multiple hits include aliases`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", null, null, null, true), "/results/globalSearch/search_results_sams_aliases.json")
  }

  @Test
  fun `can perform a match on a first name,last name and gender as male`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", Gender.M, null, null), "/results/globalSearch/search_results_sam1.json")
  }

  @Test
  fun `can perform a match on a first name,last name and gender as female`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", Gender.F, null, null), "/results/globalSearch/search_results_sam2.json")
  }

  @Test
  fun `can perform a match on a first name,last name and gender as unknown`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", Gender.UK, null, null), "/results/globalSearch/search_results_sam4.json")
  }

  @Test
  fun `can perform a match on a first name,last name and gender as not specified`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", Gender.NS, null, null), "/results/globalSearch/search_results_sam3.json")
  }

  @Test
  fun `can perform a match on a first name,last name and all genders`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", Gender.ALL, null, null), "/results/globalSearch/search_results_sams.json")
  }

  @Test
  fun `can perform a match on a first, last name and date of birth`() {
    globalSearch(GlobalSearchCriteria(null, "sam", "jones", null, null, LocalDate.of(1975 , 5 , 15)), "/results/globalSearch/search_results_sam4.json")
  }

  @Test
  fun `can perform a match on first and last name only in wrong date of birth`() {
    globalSearch(GlobalSearchCriteria(null, "john", "smith", null,null,  LocalDate.of(1970,12,25)), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a match on a first name only filter by all locations`() {
    globalSearch(GlobalSearchCriteria(null, "john", null, null,"ALL", null), "/results/globalSearch/search_results_johns_in.json")
  }

  @Test
  fun `can perform a match on first and last name only in specific location`() {
    globalSearch(GlobalSearchCriteria(null, "john", "smith", null,"IN",  null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a match on first and last name only in wrong location`() {
    globalSearch(GlobalSearchCriteria(null, "john", "smith", null,"OUT",  null), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a match on first and last name in alias`() {
    globalSearch(GlobalSearchCriteria(null, "master", "cordian", null,null ,null,true ), "/results/globalSearch/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but they must be from the same record`() {
    globalSearch(GlobalSearchCriteria(null, "master", "stark", null, null, null,true), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a match on first and last name in alias but they must be from the same record matches`() {
    globalSearch(GlobalSearchCriteria(null, "tony", "stark", null, null, null, true), "/results/globalSearch/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on firstname only in alias`() {
    globalSearch(GlobalSearchCriteria(null, "master", null, null, null, null, true), "/results/globalSearch/search_results_smyth.json")
  }

  @Test
  fun `can perform a match on last name only in alias`() {
    globalSearch(GlobalSearchCriteria(null, null, "cordian", null, null, null, true), "/results/globalSearch/search_results_smyth.json")
  }
//
//  @Test
//  fun `can perform a match on last name and gender in alias`() {
//    globalSearch(GlobalSearchCriteria(null, null, "TON", Gender.M, null, null, true), "/results/globalSearch/search_results_sam5.json")
//  }
//
//  @Test
//  fun `can perform a match on last name and date of birth in alias`() {
//    globalSearch(GlobalSearchCriteria(null, null, "TON", null, null, LocalDate.of(1990,8,15), true), "/results/globalSearch/search_results_sam5.json")
//  }

  @Test
  fun `can perform a match on first and last name in alias but with alias search off`() {
    globalSearch(GlobalSearchCriteria(null, "master", "cordian", null, null, null, false ), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a match on firstname only in alias but with alias search off`() {
    globalSearch(GlobalSearchCriteria(null, "master", null, null, null, null, false), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a match on last name only in alias but with alias search off`() {
    globalSearch(GlobalSearchCriteria(null, null, "cordian", null, null, null, false), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a match on a last name and specific location`() {
    globalSearch(GlobalSearchCriteria(null, null, "smyth", null,  "IN", null), "/results/globalSearch/search_results_smyth.json")
  }

  @Test
  fun `can perform a which returns no results as not in that location`() {
    globalSearch(GlobalSearchCriteria(null, null, "smyth", null, "OUT",null), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform a which returns result for ID search as in correct location`() {
    globalSearch(GlobalSearchCriteria("A7089EY", null, null, null,"IN", null), "/results/globalSearch/search_results_smith.json")
  }

  @Test
  fun `can perform a which returns no results as not in that prison by id`() {
    globalSearch(GlobalSearchCriteria("A7089EY", null, null, null,"OUT", null), "/results/globalSearch/empty.json")
  }

  @Test
  fun `can perform search which returns 6 result from first page`(){
    globalSearchPagination(GlobalSearchCriteria(null, "sam", "jones", null, null, null, true),100,0, "/results/globalSearch/search_results_sam_pagination1.json")
  }

  @Test
  fun `can perform search which returns 1 result from second page`(){
    globalSearchPagination(GlobalSearchCriteria(null, "sam", "jones", null, null, null),1,1, "/results/globalSearch/search_results_sam_pagination2.json")
  }

  @Test
  fun `can perform search which returns 2 result from third page`(){
    globalSearchPagination(GlobalSearchCriteria(null, "sam", "jones", null, null, null),2,2, "/results/globalSearch/search_results_sam_pagination3.json")
  }

}



