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
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH role`() {

    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_PRISONER_SEARCH role`() {

    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH role`() {

    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `find by whole prisoner number`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by whole lowercase prisoner number `() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "a7089Ey", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by prisoner number with a wildcard single letter`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "A7089?Y", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by prisoner number with wildcard suffix`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "A7089*", prisonIds = listOf("MDI")),
      expectedCount = 3,
      expectedPrisoners = listOf("A7089EY", "A7089FA", "A7089FB"),
    )
  }

  @Test
  fun `find by whole PNC number with short year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/394773H", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by whole PNC number with long year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "2015/1234S", prisonIds = listOf("WSI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A9999AA"),
    )
  }

  @Test
  fun `find by lowercase PNC number with short year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/394773h", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by lowercase PNC with long year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "2012/394773h", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by PNC number with wildcard single digit`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/39477?H", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by PNC number with a wildcard suffix and matching surname`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/394773*", lastName = "smith", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by whole CRO number`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/12J", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by lowercase CRO number `() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/12j", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by CRO number with wildcard single letter`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/1?J", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY")
    )
  }

  @Test
  fun `find by CRO number with wildcard suffix`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/*J", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by criteria that do not match any prisoners - empty result`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "trevor", pncNumber = "29906/12J", prisonIds = listOf("MDI")),
      expectedCount = 0,
      expectedPrisoners = emptyList(),
    )
  }

  @Test
  fun `find by first name`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "john", prisonIds = listOf("LEI", "MDI")),
      expectedCount = 2,
      expectedPrisoners = listOf("A7089EZ", "A7089EY"),
    )
  }

  @Test
  fun `find by last name`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(lastName = "smith", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by first and last names`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "sam", lastName = "jones", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 7,
      expectedPrisoners = listOf("A7090AB", "A7090AC", "A7090AD", "A7090BA", "A7090BB", "A7090BC", "A7090AF"),
    )
  }

  @Test
  fun `find by mixed case first and last names`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "Sam", lastName = "Jones", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 7,
      expectedPrisoners = listOf("A7090AB", "A7090AC", "A7090AD", "A7090BA", "A7090BB", "A7090BC", "A7090AF"),
    )
  }

  @Test
  fun `find by first and last names in alias`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "danny", lastName = "colin", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by mixed case first and last names in alias`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "DANny", lastName = "COLin", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by first and last names in alias with wildcard letters`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "dann?", lastName = "col?n", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by main first and last name with single wildcard letters`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "jimb?b", lastName = "j?cks", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by mixed case first and last name with single wildcard letters`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "JIMb?b", lastName = "j?cKs", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `no-terms query should match all prisoners in the specified location`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(prisonIds = listOf("MDI")),
      expectedCount = 6,
      expectedPrisoners = listOf("A7089EY", "A7089FA", "A7089FB", "A7090AA", "A7090AB", "A7090BB"),
    )
  }
}
