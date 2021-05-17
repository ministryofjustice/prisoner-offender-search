package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest

class KeywordSearchResourceTest : QueueIntegrationTest() {

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

    webTestClient.post().uri("/keyword")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("LEI", "MDI")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no filtering prison IDs provided`() {

    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = emptyList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a keyword search for prisoner number`() {
    keywordSearch(
      KeywordRequest(orWords = "A7089EY", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a match with OR words on incorrect prisoner number but correct name`() {
    keywordSearch(
      KeywordRequest(orWords = "X7089EY john smith", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a keyword AND search on exact PNC number`() {
    keywordSearch(
      KeywordRequest(andWords = "12/394773H", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a keyword AND search on PNC number with short year`() {
    keywordSearch(
      KeywordRequest(andWords = "15/1234S", prisonIds = listOf("WSI")),
      "/results/keywordSearch/search_results_pnc.json"
    )
  }

  @Test
  fun `can perform a keyword AND search on PNC number with long year`() {
    keywordSearch(
      KeywordRequest(andWords = "2015/1234S", prisonIds = listOf("WSI")),
      "/results/keywordSearch/search_results_pnc.json"
    )
  }

  @Test
  fun `can perform a keyword AND search on PNC number long year 20th century`() {
    keywordSearch(
      KeywordRequest(andWords = "1989/4444S", prisonIds = listOf("WSI")),
      "/results/keywordSearch/search_results_pnc2.json"
    )
  }

  @Test
  fun `can perform a keyword OR search on CRO number`() {
    keywordSearch(
      KeywordRequest(orWords = "29906/12J", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `cannot find by keyword AND when there is no exact match for all terms`() {
    keywordSearch(
      KeywordRequest(andWords = "trevor willis", prisonIds = listOf("MDI")),
      "/results/keywordSearch/empty.json"
    )
  }

  @Test
  fun `can perform a keyword EXACT phrase search on first name`() {
    keywordSearch(
      KeywordRequest(exactPhrase = "john", prisonIds = listOf("LEI", "MDI")),
      "/results/keywordSearch/search_results_john.json"
    )
  }

  @Test
  fun `can perform a keyword EXACT phrase search on last name`() {
    keywordSearch(
      KeywordRequest(exactPhrase = "smith", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a keyword AND search on first and last name with multiple hits`() {
    keywordSearch(
      KeywordRequest(andWords = "sam jones", prisonIds = listOf("MDI", "AGI", "LEI")),
      "/results/keywordSearch/search_results_sams.json"
    )
  }

  @Test
  fun `can perform a keyword AND search on both names in aliases`() {
    keywordSearch(
      KeywordRequest(andWords = "danny colin", prisonIds = listOf("LEI")),
      "/results/keywordSearch/search_results_alias_danny_colin.json"
    )
  }

  @Test
  fun `can perform a keyword OR search for all male gender prisoners in Moorland`() {
    keywordSearch(
      KeywordRequest(orWords = "male", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_male_MDI.json"
    )
  }

  @Test
  fun `can perform a keyword AND search on first name, last name and gender as female`() {
    keywordSearch(
      KeywordRequest(andWords = "sam jones female", prisonIds = listOf("AGI")),
      "/results/keywordSearch/search_results_sam2.json"
    )
  }

  @Test
  fun `can perform a keyword OR search to match last name in alias`() {
    keywordSearch(
      KeywordRequest(orWords = "cordian", prisonIds = listOf("LEI")),
      "/results/keywordSearch/search_results_smyth.json"
    )
  }

  @Test
  fun `can perform a combined AND and OR search for alias name and gender`() {
    keywordSearch(
      KeywordRequest(andWords = "orange female", orWords = "jimbob jacks", prisonIds = listOf("LEI")),
      "/results/keywordSearch/search_results_sam5.json"
    )
  }

  @Test
  fun `can perform a keyword OR search which returns no results as prison id is not matched`() {
    keywordSearch(
      KeywordRequest(orWords = "A7089EY", prisonIds = listOf("XXX")),
      "/results/keywordSearch/empty.json"
    )
  }

  @Test
  fun `can perform a keyword OR search and filter by a NOT term`() {
    keywordSearch(
      KeywordRequest(orWords = "sam", notWords = "female", prisonIds = listOf("MDI", "AGI", "LEI")),
      "/results/keywordSearch/search_results_sam_no_female.json"
    )
  }

  @Test
  fun `can perform a keyword no-terms query to match all prisoners in one location`() {
    keywordSearch(
      KeywordRequest(prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_all_MDI.json"
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase prisoner number `() {
    keywordSearch(
      KeywordRequest(orWords = "a7089Ey", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase CRO number `() {
    keywordSearch(
      KeywordRequest(orWords = "29906/12j", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase PNC number `() {
    keywordSearch(
      KeywordRequest(orWords = "12/394773h", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase PNC long year number `() {
    keywordSearch(
      KeywordRequest(orWords = "2012/394773h", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_smith.json"
    )
  }

  @Test
  fun `can find those prisoner who are on remand in Moorland`() {
    keywordSearch(
      KeywordRequest(exactPhrase = "remand", prisonIds = listOf("MDI")),
      "/results/keywordSearch/search_results_remand_moorland.json"
    )
  }
}
