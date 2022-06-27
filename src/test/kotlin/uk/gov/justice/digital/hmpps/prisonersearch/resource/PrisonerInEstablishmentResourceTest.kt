package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerInEstablishmentRequest

class PrisonerInEstablishmentResourceTest : QueueIntegrationTest() {

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
    webTestClient.post().uri("/establishment/MDI/prisoner")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.post().uri("/establishment/MDI/prisoner")
      .body(
        BodyInserters.fromValue(
          gson.toJson(
            PrisonerInEstablishmentRequest(
              term = "smith jones",
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
  fun `can perform a search with ROLE_GLOBAL_SEARCH role`() {
    webTestClient.post().uri("/establishment/MDI/prisoner")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerInEstablishmentRequest(term = "smith jones"))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a search with ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/establishment/MDI/prisoner")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerInEstablishmentRequest(term = "smith jones"))))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a search with both ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH roles`() {
    webTestClient.post().uri("/establishment/MDI/prisoner")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerInEstablishmentRequest(term = "smith jones"))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a search for prisoner number`() {
    search(
      request = PrisonerInEstablishmentRequest(term = "A7089EY"),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }
  fun search(
    request: PrisonerInEstablishmentRequest,
    prisonId: String = "MDI",
    expectedCount: Int = 0,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response = webTestClient.post().uri("/establishment/$prisonId/prisoner")
      .body(BodyInserters.fromValue(gson.toJson(request)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(RestResponsePage::class.java)
      .returnResult().responseBody

    Assertions.assertThat(response.numberOfElements).isEqualTo(expectedCount)
    Assertions.assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    Assertions.assertThat(response.content).extracting("prisonerNumber").containsAll(expectedPrisoners)
  }
}
