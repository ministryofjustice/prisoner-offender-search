package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.withinPercentage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.RestrictedPatientSearchCriteria

class RestrictedPatientsSearchResourceTest : QueueIntegrationTest() {

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

  @Nested
  inner class Authorisation {
    @Test
    fun `access forbidden when no authority`() {

      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {

      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can perform a match for ROLE_GLOBAL_SEARCH role`() {

      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `can perform a match for ROLE_PRISONER_SEARCH role`() {

      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `can perform a match for ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH role`() {

      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  inner class SearchAll {
    @Test
    fun `finds all restricted patients when no criteria provided`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, null),
        "/results/restrictedPatientsSearch/search_results_all.json"
      )
    }
  }

  @Nested
  inner class PrisonerNumber {
    @Test
    fun `does not match when number is of active prisoner`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("A7089EY", null, null),
        "/results/restrictedPatientsSearch/empty.json"
      )
    }

    @Test
    fun `can perform a match on prisoner number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("A9999RB", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }

    @Test
    fun `can perform a match on prisoner number lowercase`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("a9999rb", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }

    @Test
    fun `can perform a match wrong prisoner number but correct name`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("X7089EY", "HOSP", "PATIENTONE"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }
  }

  @Nested
  inner class PNCNumber {
    @Test
    fun `can perform a match on PNC number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("2014/009773W", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }

    @Test
    fun `can perform a match on PNC number short year`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("14/9773W", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }

    @Test
    fun `can perform a match on PNC number long year`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("2014/9773W", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }
  }

  @Nested
  inner class CRONumber {
    @Test
    fun `can perform a match on CRO number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("29913/12L", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }
  }

  @Nested
  inner class Booking {
    @Test
    fun `can perform a match on book number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("V69687", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }

    @Test
    fun `can perform a match on booking Id`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("1999992", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }
  }

  @Nested
  inner class Name {
    @Test
    fun `can not match when name is mis-spelt`() {
      restrictedPatientSearch(RestrictedPatientSearchCriteria(null, "PSYHOS", "PATIENTONE"), "/results/restrictedPatientsSearch/empty.json")
    }

    @Test
    fun `does not match when name is of active prisoner`() {
      restrictedPatientSearch(RestrictedPatientSearchCriteria(null, "JOHN", "SMYTH"), "/results/restrictedPatientsSearch/empty.json")
    }

    @Test
    fun `can perform a match on a first name only`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "hosp", null),
        "/results/restrictedPatientsSearch/search_results_hosp.json"
      )
    }

    @Test
    fun `can perform a match on a last name only`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, "patienttwo"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_two.json"
      )
    }

    @Test
    fun `can perform a match on first and last name only`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "hosp", "patienttwo"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_two.json"
      )
    }
  }

  @Nested
  inner class Alias {
    @Test
    fun `does not match aliases from active prisoners`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "master", null),
        "/results/restrictedPatientsSearch/empty.json"
      )
    }

    @Test
    fun `can perform a match on a first and last name only multiple hits include aliases`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "PSYHOSP", "PATIENTONE"),
        "/results/restrictedPatientsSearch/search_results_patient_one_aliases.json"
      )
    }

    @Test
    fun `can perform a match on first and last name in alias but they must be from the same record`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "PSYHOSP", "OTHERALIAS"),
        "/results/restrictedPatientsSearch/empty.json"
      )
    }

    @Test
    fun `can perform a match on firstname only in alias`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "AN", null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }

    @Test
    fun `can perform a match on last name only in alias`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, "OTHERALIAS"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json"
      )
    }
  }

  @Nested
  inner class Pagination {
    @Test
    fun `can perform search which returns 1 result from first page`() {
      restrictedPatientSearchPagination(
        RestrictedPatientSearchCriteria(null, "HOSP", null),
        1,
        0,
        "/results/restrictedPatientsSearch/search_results_hosp_pagination1.json"
      )
    }

    @Test
    fun `can perform search which returns 1 result from second page`() {
      restrictedPatientSearchPagination(
        RestrictedPatientSearchCriteria(null, "HOSP", null),
        1,
        1,
        "/results/restrictedPatientsSearch/search_results_hosp_pagination2.json"
      )
    }
  }

  @Test
  fun `telemetry is recorded`() {
    webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
      .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk

    verify(telemetryClient).trackEvent(
      eq("POSFindRestrictedPatientsByCriteria"),
      any(),
      com.nhaarman.mockitokotlin2.check<Map<String, Double>> {
        Assertions.assertThat(it["numberOfResults"]).isCloseTo(4.0, withinPercentage(1))
      }
    )
  }
}
