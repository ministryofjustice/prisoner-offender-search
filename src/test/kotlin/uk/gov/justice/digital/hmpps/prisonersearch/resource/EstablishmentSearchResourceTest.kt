package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest

class EstablishmentSearchResourceTest : QueueIntegrationTest() {

  companion object {
    var initialiseSearchData = true
  }

  @BeforeEach
  fun loadPrisoners() {
    if (initialiseSearchData) {
      loadPrisoners(PrisonerBuilder(prisonerNumber = "A7089EY", firstName = "SMITH", lastName = "JONES", agencyId = "MDI"))
      initialiseSearchData = false
    }
  }

  // for now this actually tests /keyword but gives the foundation of what the test loader will do

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
      .body(
        BodyInserters.fromValue(
          gson.toJson(
            KeywordRequest(
              orWords = "smith jones",
              prisonIds = listOf("LEI", "MDI")
            )
          )
        )
      )
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `can perform a keyword search for ROLE_GLOBAL_SEARCH role`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a keyword search for ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a keyword search for ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a keyword search for prisoner number`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "A7089EY", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }
}
