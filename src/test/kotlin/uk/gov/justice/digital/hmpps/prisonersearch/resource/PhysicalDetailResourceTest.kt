@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PhysicalDetailRequest

class PhysicalDetailResourceTest : AbstractSearchDataIntegrationTest() {
  // setup is done by the parent class, setting up the standard set of search data from wiremock mappings files

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
      .bodyValue(
        PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("LEI", "MDI"))
      )
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no filtering prison IDs provided`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(PhysicalDetailRequest(minHeight = 100, prisonIds = emptyList()))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when multiple prisons and cell location prefix supplied`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(
        PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI", "LEI"), cellLocationPrefix = "ABC-1")
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when heights less than 0`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(
        PhysicalDetailRequest(minHeight = -100, maxHeight = -200, prisonIds = listOf("MDI"))
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when heights inverted`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(
        PhysicalDetailRequest(minHeight = 100, maxHeight = 50, prisonIds = listOf("MDI"))
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when weights inverted`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(
        PhysicalDetailRequest(minWeight = 100, maxWeight = 50, prisonIds = listOf("MDI"))
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when weights less than 0`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(
        PhysicalDetailRequest(minWeight = -100, maxWeight = -200, prisonIds = listOf("MDI"))
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH role`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI")))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI")))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `will page the results - first page limited to size`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(
      minHeight = 100, prisonIds = listOf("MDI", "LEI"), pagination = PaginationRequest(0, 2)
    ),
    expectedPrisoners = listOf("A1090AA", "A7089EY"),
  )

  @Test
  fun `will page the results - second page shows remaining prisoners`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(
      minHeight = 100,
      prisonIds = listOf("MDI", "LEI"),
      pagination = PaginationRequest(1, 2)
    ),
    expectedPrisoners = listOf("A7089EZ", "A7090BA"),
  )

  @Nested
  inner class `height and weight tests`() {
    // getBooking_A1090AA.json: location: MDI-H-1-004,  heightCentimetres: 202, weightKilograms: 100
    // getBooking_A7089EY.json: location: MDI-A-1-001,  heightCentimetres: 165, weightKilograms: 57
    // getBooking_A7089EZ.json: location: LEI-B-C1-010, heightCentimetres: 188, weightKilograms: 99
    // getBooking_A7090BA.json: location: LEI-B-C1-010, heightCentimetres: 188, weightKilograms: 99
    // getBooking_A7090BB.json: location: MDI-A-1-003,  heightCentimetres: 200, weightKilograms: 80
    @Test
    fun `find by minimum height`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minHeight = 170, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A1090AA", "A7090BB"),
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
  }

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

  @Nested
  inner class `gender and ethnicity tests`() {
    // getBooking_A7089EZ.json: location: LEI-B-C1-010, alias gender: "Not Known / Not Recorded"
    // getBooking_A7090AC.json: location: AGI-H-1-004,  ethnicity: "White: Any other background"
    // getBooking_A7090AD.json: location: AGI-H-1-004,  gender: "Not Known / Not Recorded"
    // getBooking_A7090BA.json: location: LEI-B-C1-010, ethnicity: "Prefer not to say", alias ethnicity: "White: Any other background"
    // getBooking_A7090BC.json: location: AGI-H-1-004,  ethnicity: "Prefer not to say"
    @Test
    fun `find by gender`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(gender = "Female", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("A7090AC", "A7090BC"),
    )

    @Test
    fun `find by gender includes aliases`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(gender = "Not Known / Not Recorded", prisonIds = listOf("AGI", "LEI")),
      expectedPrisoners = listOf("A7089EZ", "A7090AD"),
    )

    @Test
    fun `find by ethnicity`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(ethnicity = "Prefer not to say", prisonIds = listOf("AGI", "LEI")),
      expectedPrisoners = listOf("A7090BA", "A7090BC"),
    )

    @Test
    fun `find by ethnicity includes aliases`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI")
      ),
      expectedPrisoners = listOf("A7090AC", "A7090BA"),
    )
  }

  private fun physicalDetailSearch(
    detailRequest: PhysicalDetailRequest,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response = webTestClient.post().uri("/physical-detail")
      .bodyValue(detailRequest)
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
