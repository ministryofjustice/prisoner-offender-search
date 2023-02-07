@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.AliasBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.PhysicalCharacteristicBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PhysicalDetailRequest

class PhysicalDetailResourceTest : QueueIntegrationTest() {
  private companion object {
    private var initialiseSearchData = true
  }

  @BeforeEach
  fun loadPrisoners() {
    if (initialiseSearchData) {
      listOf(
        // height / weight test data
        PrisonerBuilder(
          prisonerNumber = "H1090AA", agencyId = "MDI", cellLocation = "H-1-004", heightCentimetres = 202, weightKilograms = 100,
        ),
        PrisonerBuilder(
          prisonerNumber = "H7089EY", agencyId = "MDI", cellLocation = "A-1-001", heightCentimetres = 165, weightKilograms = 57,
        ),
        PrisonerBuilder(
          prisonerNumber = "H7089EZ", agencyId = "LEI", cellLocation = "B-C1-010", heightCentimetres = 188, weightKilograms = 99,
        ),
        PrisonerBuilder(
          prisonerNumber = "H7090BA", agencyId = "LEI", cellLocation = "B-C1-010", heightCentimetres = 200, weightKilograms = 99,
        ),
        PrisonerBuilder(
          prisonerNumber = "H7090BB", agencyId = "MDI", cellLocation = "A-1-003", heightCentimetres = 200, weightKilograms = 80,
        ),

        PrisonerBuilder(
          prisonerNumber = "G7089EZ", agencyId = "LEI", cellLocation = "B-C1-010", gender = "Male",
          aliases = listOf(AliasBuilder(gender = "Not Known / Not Recorded")),
          physicalCharacteristics = PhysicalCharacteristicBuilder(
            hairColour = "Red",
            rightEyeColour = "Green",
            leftEyeColour = "Hazel",
            facialHair = "Clean Shaven",
            shapeOfFace = "Round",
            build = "Proportional"
          ),
        ),
        PrisonerBuilder(
          prisonerNumber = "G7090AC",
          agencyId = "AGI",
          cellLocation = "H-1-004",
          gender = "Female",
          ethnicity = "White: Any other background",
          physicalCharacteristics = PhysicalCharacteristicBuilder(
            hairColour = "Balding",
            rightEyeColour = "Clouded",
            leftEyeColour = "Brown",
            facialHair = "Goatee Beard",
            shapeOfFace = "Bullet",
            build = "Obese"
          ),
        ),
        PrisonerBuilder(
          prisonerNumber = "G7090AD", agencyId = "AGI", cellLocation = "H-1-004", gender = "Not Known / Not Recorded",
          physicalCharacteristics = PhysicalCharacteristicBuilder(
            hairColour = "Red",
            rightEyeColour = "Green",
            leftEyeColour = "Hazel",
            facialHair = "Clean Shaven",
            shapeOfFace = "Round",
            build = "Proportional"
          ),
        ),
        PrisonerBuilder(
          prisonerNumber = "G7090BA", agencyId = "LEI", cellLocation = "B-C1-010",
          gender = "Male",
          ethnicity = "Prefer not to say",
          aliases = listOf(AliasBuilder(ethnicity = "White: Any other background")),
          physicalCharacteristics = PhysicalCharacteristicBuilder(
            hairColour = "Mouse",
            rightEyeColour = "Missing",
            leftEyeColour = "Missing",
            facialHair = "Not Asked",
            shapeOfFace = "Oval",
            build = "Muscular"
          ),
        ),
        PrisonerBuilder(
          prisonerNumber = "G7090BC",
          agencyId = "AGI",
          cellLocation = "H-1-004",
          gender = "Female",
          ethnicity = "Prefer not to say",
          physicalCharacteristics = PhysicalCharacteristicBuilder(
            hairColour = "Red",
            rightEyeColour = "Green",
            leftEyeColour = "Hazel",
            facialHair = "Clean Shaven",
            shapeOfFace = "Round",
            build = "Proportional"
          ),
        ),
      ).apply { loadPrisoners(this) }
      initialiseSearchData = false
    }
  }

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
    expectedPrisoners = listOf("H1090AA", "H7089EY"),
  )

  @Test
  fun `will page the results - second page shows remaining prisoners`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(
      minHeight = 100,
      prisonIds = listOf("MDI", "LEI"),
      pagination = PaginationRequest(1, 2)
    ),
    expectedPrisoners = listOf("H7089EZ", "H7090BA"),
  )

  @Nested
  inner class `height and weight tests` {
    @Test
    fun `find by minimum height`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minHeight = 170, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    )

    @Test
    fun `find by maximum height`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(maxHeight = 200, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H7089EY", "H7090BB"),
    )

    @Test
    fun `find by exact height`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minHeight = 200, maxHeight = 200, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H7090BB"),
    )

    @Test
    fun `find by height range`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minHeight = 100, maxHeight = 200, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H7089EY", "H7090BB"),
    )

    @Test
    fun `find by minimum weight`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 70, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    )

    @Test
    fun `find by maximum weight`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(maxWeight = 100, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7089EY", "H7090BB"),
    )

    @Test
    fun `find by exact weight`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 100, maxWeight = 100, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA"),
    )

    @Test
    fun `find by weight range`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 80, maxWeight = 150, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    )

    @Test
    fun `height and weight are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 80, maxWeight = 150, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    ) {
      assertThat(it).extracting("heightCentimetres").containsExactly(202, 200)
      assertThat(it).extracting("weightKilograms").containsExactly(100, 80)
    }
  }

  @Nested
  inner class `gender and ethnicity tests` {
    @Test
    fun `find by gender`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(gender = "Female", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AC", "G7090BC"),
    )

    @Test
    fun `find by gender includes aliases`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(gender = "Not Known / Not Recorded", prisonIds = listOf("AGI", "LEI")),
      expectedPrisoners = listOf("G7089EZ", "G7090AD"),
    )

    @Test
    fun `find by ethnicity`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(ethnicity = "Prefer not to say", prisonIds = listOf("AGI", "LEI")),
      expectedPrisoners = listOf("G7090BA", "G7090BC"),
    )

    @Test
    fun `find by ethnicity includes aliases`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI")
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    )

    @Test
    fun `gender and ethnicity are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI")
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    ) {
      assertThat(it).extracting("gender").containsExactly("Female", "Male")
      assertThat(it).extracting("ethnicity").containsExactly("White: Any other background", "Prefer not to say")
    }
  }

  @Nested
  inner class `physical characteristics tests` {
    @Test
    fun `find by hair colour`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(hairColour = "Red", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by right eye colour`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(rightEyeColour = "Green", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by left eye colour`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(leftEyeColour = "Hazel", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by facial hair`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(facialHair = "Clean Shaven", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by shape of face`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(shapeOfFace = "Round", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by build`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(build = "Proportional", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `physical characteristics are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI")
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    ) {
      assertThat(it).extracting("hairColour").containsExactly("Balding", "Mouse")
      assertThat(it).extracting("rightEyeColour").containsExactly("Clouded", "Missing")
      assertThat(it).extracting("leftEyeColour").containsExactly("Brown", "Missing")
      assertThat(it).extracting("facialHair").containsExactly("Goatee Beard", "Not Asked")
      assertThat(it).extracting("shapeOfFace").containsExactly("Bullet", "Oval")
      assertThat(it).extracting("build").containsExactly("Obese", "Muscular")
    }
  }

  private fun physicalDetailSearch(
    detailRequest: PhysicalDetailRequest,
    expectedPrisoners: List<String> = emptyList(),
    extraContentAssertions: (param: MutableList<out Any?>?) -> Unit = {},
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
    extraContentAssertions(response.content)
  }
}
