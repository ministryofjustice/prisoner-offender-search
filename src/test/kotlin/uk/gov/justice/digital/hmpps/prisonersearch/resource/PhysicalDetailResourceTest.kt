package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PhysicalDetailRequest

class PhysicalDetailResourceTest : AbstractSearchDataIntegrationTest() {
  @Test
  fun `access forbidden when no authority`() {
    webTestClient.post().uri("/physical-detail")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.post().uri("/physical-detail")
      .body(
        BodyInserters.fromValue(
          gson.toJson(
            PhysicalDetailRequest(
              minHeight = 100,
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
  fun `bad request when no filtering prison IDs provided`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minHeight = 100, prisonIds = emptyList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when multiple prisons and cell location prefix supplied`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI", "LEI"), cellLocationPrefix = "ABC-1"))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when heights less than 0`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minHeight = -100, maxHeight = -200, prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when heights inverted`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minHeight = 100, maxHeight = 50, prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when weights inverted`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minWeight = 100, maxWeight = 50, prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when weights less than 0`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minWeight = -100, maxWeight = -200, prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH role`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `will page the results - first page limited to size`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI", "LEI"), pagination = PaginationRequest(0, 2)),
    expectedPrisoners = listOf("A1090AA", "A7089EY"),
    numberOfElements = 5,
  )

  @Test
  fun `will page the results - second page shows remaining prisoners`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI", "LEI"), pagination = PaginationRequest(1, 2)),
    expectedPrisoners = listOf("A7089EZ", "A7090BA"),
    numberOfElements = 5,
  )

  @Test
  fun `find by minimum height`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A1090AA", "A7089EY", "A7090BB"),
  )

  @Test
  fun `find by maximum height`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(maxHeight = 200, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A7089EY", "A7090BB"),
  )

  @Test
  fun `find by exact height`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 200, maxHeight = 200, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A7090BB"),
  )

  @Test
  fun `find by height range`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, maxHeight = 200, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A7089EY", "A7090BB"),
  )

  @Test
  fun `find by cell location with prison prefix`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI"), cellLocationPrefix = "MDI-A"),
    expectedPrisoners = listOf("A7089EY", "A7090BB"),
  )

  @Test
  fun `find by cell location without prison prefix`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI"), cellLocationPrefix = "A"),
    expectedPrisoners = listOf("A7089EY", "A7090BB"),
  )

  @Test
  fun `find by minimum weight`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minWeight = 70, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A1090AA", "A7090BB"),
  )

  @Test
  fun `find by maximum weight`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(maxWeight = 100, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A1090AA", "A7089EY", "A7090BB"),
  )

  @Test
  fun `find by exact weight`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minWeight = 100, maxWeight = 100, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A1090AA"),
  )

  @Test
  fun `find by weight range`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minWeight = 80, maxWeight = 150, prisonIds = listOf("MDI")),
    expectedPrisoners = listOf("A1090AA", "A7090BB"),
  )

  private fun physicalDetailSearch(
    detailRequest: PhysicalDetailRequest,
    numberOfElements: Int = 0,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response = webTestClient.post().uri("/physical-detail")
      .body(BodyInserters.fromValue(gson.toJson(detailRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(RestResponsePage::class.java)
      .returnResult().responseBody

    assertThat(response.content).extracting("prisonerNumber").containsExactlyElementsOf(expectedPrisoners)
    assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    assertThat(response.numberOfElements).isEqualTo(expectedPrisoners.size)
  }
}
