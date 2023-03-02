package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.ReleaseDateSearch
import java.time.LocalDate

class PrisonerSearchByReleaseDateResourceTest : AbstractSearchDataIntegrationTest() {
  @Test
  fun `access forbidden when no authority`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .body(BodyInserters.fromValue(gson.toJson(ReleaseDateSearch(latestReleaseDate = LocalDate.now()))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no upper bound provided`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .body(BodyInserters.fromValue(gson.toJson(ReleaseDateSearch(latestReleaseDate = null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search - latestReleaseDateRange is a required field")
  }

  @Test
  fun `bad request when the upper bound is a date before the lower bound`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .body(
        BodyInserters.fromValue(
          gson.toJson(
            ReleaseDateSearch(
              earliestReleaseDate = LocalDate.parse("2022-01-02"),
              latestReleaseDate = LocalDate.parse("2022-01-01"),
            ),
          ),
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search - latestReleaseDate must be on or before the earliestReleaseDate")
  }

  @Test
  fun `can match on conditionalReleaseDate`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2023-05-16"),
        latestReleaseDate = LocalDate.parse("2023-05-16"),
      ),
      "/results/releaseDateSearch/search_conditional_release_date.json",
    )
  }

  @Test
  fun `can match on confirmedReleaseDate`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2023-04-01"),
        latestReleaseDate = LocalDate.parse("2023-04-01"),
      ),
      "/results/releaseDateSearch/search_confirmed_release_date.json",
    )
  }

  @Test
  fun `can match on date range with mix of confirmedReleaseDate and conditionalReleaseDate`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2020-04-28"),
        latestReleaseDate = LocalDate.parse("2023-04-01"),
      ),
      "/results/releaseDateSearch/search_date_range.json",
    )
  }

  @Test
  fun `can filter date range by prison code`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2011-01-05"),
        latestReleaseDate = LocalDate.parse("2030-01-31"),
        prisonIds = setOf("MDI", "WSI"),
      ),
      "/results/releaseDateSearch/search_date_range_filtered_by_prison.json",
    )
  }

  @Test
  fun `can paginate results - 8 results on page 1`() {
    searchByReleaseDatePagination(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2011-01-05"),
        latestReleaseDate = LocalDate.parse("2030-01-31"),
      ),
      8,
      0,
      "/results/releaseDateSearch/search_date_range_pagination_page_1.json",
    )
  }

  @Test
  fun `can paginate results - 3 results on page 2`() {
    searchByReleaseDatePagination(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2011-01-05"),
        latestReleaseDate = LocalDate.parse("2030-01-31"),
      ),
      8,
      1,
      "/results/releaseDateSearch/search_date_range_pagination_page_2.json",
    )
  }
}
