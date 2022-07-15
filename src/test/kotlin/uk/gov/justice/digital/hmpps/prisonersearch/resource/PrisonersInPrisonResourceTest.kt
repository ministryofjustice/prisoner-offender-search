package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonersInPrisonRequest

class PrisonersInPrisonResourceTest : QueueIntegrationTest() {

  companion object {
    var initialiseSearchData = true
  }

  @BeforeEach
  fun loadPrisoners() {
    if (initialiseSearchData) {
      loadPrisoners(
        PrisonerBuilder(
          prisonerNumber = "A7089EY", firstName = "SMITH", lastName = "JONES", agencyId = "MDI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1809JK", firstName = "SMITH", lastName = "JONES", agencyId = "HEI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A9809BB", firstName = "AKAN", lastName = "OBENG", agencyId = "HEI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1921BH", firstName = "SMITH", lastName = "JONES", released = true
        ),
        PrisonerBuilder(
          prisonerNumber = "A1819AA", firstName = "MARIANA", lastName = "RODRÍGUEZ", agencyId = "PEI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1809AB", firstName = "MARIANA", lastName = "RODRÍGUEZ", agencyId = "PEI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1809AC", firstName = "CAMILA", lastName = "RODRÍGUEZ", agencyId = "PEI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1809AD", firstName = "CAMILA", lastName = "MORALES", agencyId = "PEI"
        ),
      )
      initialiseSearchData = false
    }
  }

  @Nested
  inner class AccessControl {

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.post().uri("/prison/MDI/prisoners").header("Content-Type", "application/json").exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/prison/MDI/prisoners").body(
        BodyInserters.fromValue(
          gson.toJson(
            PrisonersInPrisonRequest(
              term = "smith jones",
            )
          )
        )
      ).headers(setAuthorisation()).header("Content-Type", "application/json").exchange().expectStatus().isForbidden
    }

    @Test
    fun `can perform a search with ROLE_GLOBAL_SEARCH role`() {
      webTestClient.post().uri("/prison/MDI/prisoners")
        .body(BodyInserters.fromValue(gson.toJson(PrisonersInPrisonRequest(term = "smith jones"))))
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH"))).header("Content-Type", "application/json")
        .exchange().expectStatus().isOk
    }

    @Test
    fun `can perform a search with ROLE_PRISONER_SEARCH role`() {
      webTestClient.post().uri("/prison/MDI/prisoners")
        .body(BodyInserters.fromValue(gson.toJson(PrisonersInPrisonRequest(term = "smith jones"))))
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH"))).header("Content-Type", "application/json")
        .exchange().expectStatus().isOk
    }

    @Test
    fun `can perform a search with both ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH roles`() {
      webTestClient.post().uri("/prison/MDI/prisoners")
        .body(BodyInserters.fromValue(gson.toJson(PrisonersInPrisonRequest(term = "smith jones"))))
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
        .header("Content-Type", "application/json").exchange().expectStatus().isOk
    }

    @Test
    fun `can perform a search for prisoner number`() {
      search(
        request = PrisonersInPrisonRequest(term = "A7089EY"),
        expectedCount = 1,
        expectedPrisoners = listOf("A7089EY"),
      )
    }
  }

  @Nested
  @DisplayName("When term includes a name")
  inner class TermIncludesNames {
    @Test
    internal fun `can search by just last name`() {
      search(
        request = PrisonersInPrisonRequest(term = "RODRÍGUEZ"),
        prisonId = "PEI",
        expectedPrisoners = listOf("A1819AA", "A1809AB", "A1809AC"),
      )
    }
    @Test
    internal fun `can search by just first name`() {
      search(
        request = PrisonersInPrisonRequest(term = "CAMILA"),
        prisonId = "PEI",
        expectedPrisoners = listOf("A1809AC", "A1809AD"),
      )
    }
    @Test
    internal fun `can search by first and last name`() {
      search(
        request = PrisonersInPrisonRequest(term = "MARIANA RODRÍGUEZ"),
        prisonId = "PEI",
        expectedPrisoners = listOf("A1809AB", "A1819AA"),
      )
    }
    @Test
    internal fun `can search by first and last name in any order`() {
      search(
        request = PrisonersInPrisonRequest(term = "RODRÍGUEZ MARIANA"),
        prisonId = "PEI",
        expectedPrisoners = listOf("A1809AB", "A1819AA"),
      )
    }
  }
  @Nested
  @DisplayName("When term includes a prisoner number")
  inner class TermIncludesPrisonerNumber {
    @Test
    internal fun `will only find in the prison they are active in`() {
      search(
        request = PrisonersInPrisonRequest(term = "A7089EY"),
        prisonId = "MDI",
        expectedPrisoners = listOf("A7089EY"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "A1809JK"),
        prisonId = "HEI",
        expectedPrisoners = listOf("A1809JK"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "A7089EY"),
        prisonId = "HEI",
        expectedPrisoners = listOf(),
      )
    }

    @Test
    internal fun `could find a prisoner that is OUT even though it is not officially supported`() {
      search(
        request = PrisonersInPrisonRequest(term = "A1921BH"),
        prisonId = "OUT",
        expectedPrisoners = listOf("A1921BH"),
      )
    }

    @Test
    internal fun `can search with any case`() {
      search(
        request = PrisonersInPrisonRequest(term = "A1809JK"),
        prisonId = "HEI",
        expectedPrisoners = listOf("A1809JK"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "a1809jk"),
        prisonId = "HEI",
        expectedPrisoners = listOf("A1809JK"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "a1809JK"),
        prisonId = "HEI",
        expectedPrisoners = listOf("A1809JK"),
      )
    }
    @Test
    internal fun `when prisoner number present will ignore any other term`() {
      search(
        request = PrisonersInPrisonRequest(term = "OBENG A1809JK"),
        prisonId = "HEI",
        expectedPrisoners = listOf("A1809JK"),
      )
    }
  }

  fun search(
    request: PrisonersInPrisonRequest,
    prisonId: String = "MDI",
    expectedCount: Int? = null,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response =
      webTestClient.post().uri("/prison/$prisonId/prisoners").body(BodyInserters.fromValue(gson.toJson(request)))
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH"))).header("Content-Type", "application/json")
        .exchange().expectStatus().isOk.expectBody(RestResponsePage::class.java).returnResult().responseBody

    Assertions.assertThat(response.numberOfElements).isEqualTo(expectedCount ?: expectedPrisoners.size)
    Assertions.assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    Assertions.assertThat(response.content).extracting("prisonerNumber").containsAll(expectedPrisoners)
  }
}
