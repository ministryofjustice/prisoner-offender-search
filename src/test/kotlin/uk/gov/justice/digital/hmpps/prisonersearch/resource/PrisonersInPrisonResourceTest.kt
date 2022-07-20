package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonersInPrisonRequest

@TestPropertySource(properties = ["index.page-size=1000"])
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
        PrisonerBuilder(
          prisonerNumber = "A1810AA", firstName = "YAW", lastName = "BOATENG", agencyId = "WWI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1810AC", firstName = "EKOW", lastName = "BOATENG", agencyId = "WWI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1810AB", firstName = "EKOW", lastName = "BOATENG", agencyId = "WWI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1810AD", firstName = "EKOW", lastName = "MENSAH", agencyId = "WWI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1810AE", firstName = "EKOW", lastName = "ADJEI", agencyId = "WWI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1810AF", firstName = "ADJEI", lastName = "BOATENG", agencyId = "WWI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1820AA", firstName = "MOHAMMED", lastName = "HUSSAIN", agencyId = "BXI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1820AB", firstName = "MOHAMMAD", lastName = "HUSSAIN", agencyId = "BXI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1820AC", firstName = "MOHAMAD", lastName = "HUSAIN", agencyId = "BXI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1820AD", firstName = "JOHN", lastName = "HUSAIN", agencyId = "BXI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1820AE", firstName = "JOHN", lastName = "BLATHERINGTON-SMYTHE", agencyId = "BXI"
        ),
        PrisonerBuilder(
          prisonerNumber = "A1830AA", firstName = "SMITH", lastName = "JONES", agencyId = "ACI", alertCodes = listOf("X" to "XTACT")
        ),
        PrisonerBuilder(
          prisonerNumber = "A1830AB", firstName = "SMITH", lastName = "JACK", agencyId = "ACI", alertCodes = listOf("X" to "XTACT", "W" to "WO")
        ),
        PrisonerBuilder(
          prisonerNumber = "A1830AC", firstName = "MOHAMAD", lastName = "HUSAIN", agencyId = "ACI", alertCodes = listOf("W" to "WO")
        ),
        PrisonerBuilder(
          prisonerNumber = "A1830AD", firstName = "ADJEI", lastName = "BOATENG", agencyId = "ACI", alertCodes = listOf()
        ),
        PrisonerBuilder(
          prisonerNumber = "A1830AE", firstName = "KWEKU", lastName = "BOATENG", agencyId = "ACI", alertCodes = listOf("V" to "VIP")
        ),
      )
      initialiseSearchData = false
    }
  }

  @Nested
  inner class AccessControl {

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri {
        it.path("/prison/MDI/prisoners")
          .queryParam("term", "smith jones")
          .build()
      }
        .header("Content-Type", "application/json").exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri {
        it.path("/prison/MDI/prisoners")
          .queryParam("term", "smith jones")
          .build()
      }
        .headers(setAuthorisation()).header("Content-Type", "application/json").exchange().expectStatus().isForbidden
    }

    @Test
    fun `can perform a search with ROLE_GLOBAL_SEARCH role`() {
      webTestClient.get().uri {
        it.path("/prison/MDI/prisoners")
          .queryParam("term", "smith jones")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH"))).header("Content-Type", "application/json")
        .exchange().expectStatus().isOk
    }

    @Test
    fun `can perform a search with ROLE_PRISONER_SEARCH role`() {
      webTestClient.get().uri {
        it.path("/prison/MDI/prisoners")
          .queryParam("term", "smith jones")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH"))).header("Content-Type", "application/json")
        .exchange().expectStatus().isOk
    }

    @Test
    fun `can perform a search with both ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH roles`() {
      webTestClient.get().uri {
        it.path("/prison/MDI/prisoners")
          .queryParam("term", "smith jones")
          .build()
      }
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

  @DisplayName("With no search term")
  @Nested
  inner class NoSearchTerm {
    @Test
    internal fun `will find all prisoners in the specified prison`() {
      search(
        request = PrisonersInPrisonRequest(term = ""),
        prisonId = "PEI",
        expectedPrisoners = listOf("A1819AA", "A1809AB", "A1809AC", "A1809AD"),
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

    @Test
    internal fun `will order by last name, first name then prisoner number`() {
      search(
        request = PrisonersInPrisonRequest(term = "BOATENG"),
        prisonId = "WWI",
        expectedPrisoners = listOf("A1810AF", "A1810AB", "A1810AC", "A1810AA"),
        checkOrder = true,
      )
      search(
        request = PrisonersInPrisonRequest(term = "EKOW"),
        prisonId = "WWI",
        expectedPrisoners = listOf("A1810AE", "A1810AB", "A1810AC", "A1810AD"),
        checkOrder = true,
      )
    }

    @Test
    internal fun `can partially match for first name`() {
      search(
        request = PrisonersInPrisonRequest(term = "MOHAM"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB", "A1820AC"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "MOHAMM"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "MOHAMME"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA"),
      )
    }

    @Test
    internal fun `can partially match for last name`() {
      search(
        request = PrisonersInPrisonRequest(term = "HUS"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB", "A1820AC", "A1820AD"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "HUSS"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB"),
      )
    }

    @Test
    internal fun `can partially match for last and last name at same time`() {
      search(
        request = PrisonersInPrisonRequest(term = "MOHAMMED HUSSAIN"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "MOHAM HUS"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB", "A1820AC"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "MOHAMM HUS"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB"),
      )
    }

    @Test
    internal fun `can partially match with either first or last name`() {
      search(
        request = PrisonersInPrisonRequest(term = "MOH MAD"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AB", "A1820AC"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "HUS AIN"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB", "A1820AC", "A1820AD"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "HUS AI"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB", "A1820AC", "A1820AD"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "HUSS IN"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AA", "A1820AB"),
      )
    }

    @Test
    internal fun `can partially match double barrelled names`() {
      search(
        request = PrisonersInPrisonRequest(term = "BLATHERINGTON-SMYTHE"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AE"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "JOHN BLATHERINGTON-SMYTHE"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AE"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "BLATHERINGTON-SMYTHE"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AE"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "BLATHERINGTON SMYTHE"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AE"),
      )
      search(
        request = PrisonersInPrisonRequest(term = "BLATHERING SMYTH"),
        prisonId = "BXI",
        expectedPrisoners = listOf("A1820AE"),
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

  @DisplayName("When filtering by alert")
  @Nested
  inner class AlertFilter {

    @Nested
    @DisplayName("with no search term")
    inner class WithNoSearchTerm {
      @Test
      internal fun `will find all prisoners that have that alert`() {
        search(
          request = PrisonersInPrisonRequest(alertCodes = listOf("XTACT")),
          prisonId = "ACI",
          expectedPrisoners = listOf("A1830AA", "A1830AB"),
        )
        search(
          request = PrisonersInPrisonRequest(alertCodes = listOf("WO")),
          prisonId = "ACI",
          expectedPrisoners = listOf("A1830AB", "A1830AC"),
        )
      }

      @Test
      internal fun `will find all prisoners that have at least one of the specified alerts`() {
        search(
          request = PrisonersInPrisonRequest(alertCodes = listOf("XTACT", "WO")),
          prisonId = "ACI",
          expectedPrisoners = listOf("A1830AA", "A1830AB", "A1830AC"),
        )
      }
    }
    @Nested
    @DisplayName("with a search term")
    inner class WithSearchTerm {
      @Test
      internal fun `will find all prisoners that have the alert and match the term `() {
        search(
          request = PrisonersInPrisonRequest(term = "J SMITH", alertCodes = listOf("XTACT")),
          prisonId = "ACI",
          expectedPrisoners = listOf("A1830AA", "A1830AB"),
        )
        search(
          request = PrisonersInPrisonRequest(term = "JO SMITH", alertCodes = listOf("XTACT")),
          prisonId = "ACI",
          expectedPrisoners = listOf("A1830AA"),
        )
      }

      @Test
      internal fun `will find all prisoners that have at least one of the specified alerts and match the term`() {
        search(
          request = PrisonersInPrisonRequest(term = "J SMITH", alertCodes = listOf("XTACT", "WO")),
          prisonId = "ACI",
          expectedPrisoners = listOf("A1830AA", "A1830AB"),
        )
        search(
          request = PrisonersInPrisonRequest(term = "BOATENG", alertCodes = listOf("VIP")),
          prisonId = "ACI",
          expectedPrisoners = listOf("A1830AE"),
        )
      }
    }
  }

  fun search(
    request: PrisonersInPrisonRequest,
    prisonId: String = "MDI",
    expectedCount: Int? = null,
    expectedPrisoners: List<String> = emptyList(),
    checkOrder: Boolean = false,
  ) {
    val response =
      webTestClient.get().uri {
        it.path("/prison/$prisonId/prisoners")
          .queryParam("term", request.term)
          .queryParam("page", request.pagination.page)
          .queryParam("size", request.pagination.size)
          .queryParam("alerts", request.alertCodes)
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH"))).header("Content-Type", "application/json")
        .exchange().expectStatus().isOk.expectBody(RestResponsePage::class.java).returnResult().responseBody

    assertThat(response.numberOfElements).isEqualTo(expectedCount ?: expectedPrisoners.size)
    assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    val numbers = assertThat(response.content).extracting("prisonerNumber")
    if (checkOrder) {
      numbers.isEqualTo(expectedPrisoners)
    } else {
      numbers.containsAll(expectedPrisoners)
    }
  }
}
