package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.PrisonerNumbers

class PrisonerSearchByPrisonerNumbersResourceTest : QueueIntegrationTest() {

  companion object {
    private var initialiseSearchData = true
  }

  data class IDs(val offenderNumber: String)

  @BeforeEach
  fun setup() {
    if (initialiseSearchData) {
      loadPrisoners(*getTestPrisonerNumbers(12).map { PrisonerBuilder(prisonerNumber = it) }.toTypedArray())
      initialiseSearchData = false
    }
  }

  private fun getTestPrisonerNumbers(count: Int): List<String> {
    return List(count) { i -> "AN$i" }
  }

  @Test
  fun `prisoner number search returns bad request when no prison numbers provided`() {

    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue("{\"prisonerNumbers\":[]}"))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 PrisonerNumbers")
  }

  @Test
  fun `prisoner number search returns bad request when over 1000 prison numbers provided`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(getTestPrisonerNumbers(1001)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 PrisonerNumbers")
  }

  @Test
  fun `prisoner number search returns offender records, single result`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue("""{"prisonerNumbers":["AN2"]}"""))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN2")
      .jsonPath("$[0].imprisonmentStatus").isEqualTo("LIFE")
      .jsonPath("$[0].imprisonmentStatusDescription").isEqualTo("Life imprisonment")
  }

  @Test
  fun `prisoner number search returns offender records, ignoring not found prison numbers`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(listOf("AN2", "AN33", "AN44")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN2")
  }

  @Test
  fun `prisoner number search can return over 10 hits (default max hits is 10)`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(getTestPrisonerNumbers(12)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(12)
  }

  @Test
  fun `access forbidden for prison number search when no role`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(arrayListOf("ABC")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }
}
