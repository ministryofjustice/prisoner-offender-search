package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerDetailRequest

class PrisonerDetailResourceTest : QueueIntegrationTest() {

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

    webTestClient.post().uri("/prisoner-detail")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("LEI", "MDI")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no filtering prison IDs provided`() {

    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = emptyList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `find by whole prisoner number`() {
    detailSearch(
      PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by whole lowercase prisoner number `() {
    detailSearch(
      PrisonerDetailRequest(nomsNumber = "a7089Ey", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by prisoner number with a wildcard single letter`() {
    detailSearch(
      PrisonerDetailRequest(nomsNumber = "A7089?Y", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by prisoner number with wildcard suffix`() {
    detailSearch(
      PrisonerDetailRequest(nomsNumber = "A7089*", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_wildcard_A7089.json"
    )
  }

  @Test
  fun `find by whole PNC number with short year`() {
    detailSearch(
      PrisonerDetailRequest(pncNumber = "12/394773H", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by whole PNC number with long year`() {
    detailSearch(
      PrisonerDetailRequest(pncNumber = "2015/1234S", prisonIds = listOf("WSI")),
      "/results/detailSearch/search_results_pnc.json"
    )
  }

  @Test
  fun `find by lowercase PNC number with short year`() {
    detailSearch(
      PrisonerDetailRequest(pncNumber = "12/394773h", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by lowercase PNC with long year`() {
    detailSearch(
      PrisonerDetailRequest(pncNumber = "2012/394773h", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by PNC number with wildcard single digit`() {
    detailSearch(
      PrisonerDetailRequest(pncNumber = "12/39477?H", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by PNC number with a wildcard suffix and matching surname`() {
    detailSearch(
      PrisonerDetailRequest(pncNumber = "12/394773*", lastName = "smith", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by whole CRO number`() {
    detailSearch(
      PrisonerDetailRequest(croNumber = "29906/12J", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by lowercase CRO number `() {
    detailSearch(
      PrisonerDetailRequest(croNumber = "29906/12j", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by CRO number with wildcard single letter`() {
    detailSearch(
      PrisonerDetailRequest(croNumber = "29906/1?J", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by CRO number with wildcard suffix`() {
    detailSearch(
      PrisonerDetailRequest(croNumber = "29906/*J", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by criteria that do not match any prisoners - empty result`() {
    detailSearch(
      PrisonerDetailRequest(firstName = "trevor", pncNumber = "29906/12J", prisonIds = listOf("MDI")),
      "/results/detailSearch/empty.json"
    )
  }

  @Test
  fun `find by first name`() {
    detailSearch(
      PrisonerDetailRequest(firstName = "john", prisonIds = listOf("LEI", "MDI")),
      "/results/detailSearch/search_results_john.json"
    )
  }

  @Test
  fun `find by last name`() {
    detailSearch(
      PrisonerDetailRequest(lastName = "smith", prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_smith.json"
    )
  }

  @Test
  fun `find by first and last names`() {
    detailSearch(
      PrisonerDetailRequest(firstName = "sam", lastName = "jones", prisonIds = listOf("MDI", "AGI", "LEI")),
      "/results/detailSearch/search_results_sams.json"
    )
  }

  @Test
  fun `find by first and last names in alias`() {
    detailSearch(
      PrisonerDetailRequest(firstName = "danny", lastName = "colin", prisonIds = listOf("LEI")),
      "/results/detailSearch/search_results_alias_danny_colin.json"
    )
  }

  @Test
  fun `find by first and last names in alias with wildcard letters`() {
    detailSearch(
      PrisonerDetailRequest(firstName = "dann?", lastName = "col?n", prisonIds = listOf("LEI")),
      "/results/detailSearch/search_results_alias_danny_colin.json"
    )
  }

  @Test
  fun `find by main first and last name with single wildcard letters`() {
    detailSearch(
      PrisonerDetailRequest(firstName = "jimb?b", lastName = "j?cks", prisonIds = listOf("LEI")),
      "/results/detailSearch/search_results_alias_danny_colin.json"
    )
  }

  @Test
  fun `no-terms query should match all prisoners in the specified location`() {
    detailSearch(
      PrisonerDetailRequest(prisonIds = listOf("MDI")),
      "/results/detailSearch/search_results_all_MDI.json"
    )
  }
}
