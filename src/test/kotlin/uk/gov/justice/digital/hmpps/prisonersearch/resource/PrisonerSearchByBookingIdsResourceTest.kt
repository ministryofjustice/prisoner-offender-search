package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.BookingIds
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.Duration

@TestPropertySource(properties = ["index.page-size=12"])
class PrisonerSearchByBookingIdsResourceTest : QueueIntegrationTest() {

  companion object {
    private var initialiseSearchData = true
  }

  data class IDs(val offenderNumber: String)

  @BeforeEach
  fun setup() {
    if (initialiseSearchData) {
      val prisonerNumbers = List(12) { i -> "AN$i" }
      prisonMockServer.stubFor(
        get(urlEqualTo("/api/offenders/ids"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withHeader("Total-Records", prisonerNumbers.size.toString())
              .withBody(gson.toJson(prisonerNumbers.map { IDs(it) })),
          ),
      )
      prisonerNumbers.forEachIndexed { bookingId: Int, prisonNumber: String ->
        prisonMockServer.stubFor(
          get(urlEqualTo("/api/offenders/$prisonNumber"))
            .willReturn(
              aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(getOffenderBookingJson(prisonNumber, bookingId.toLong())),
            ),
        )
      }

      setupIndexes()
      indexPrisoners(prisonerNumbers)

      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      initialiseSearchData = false
    }
  }

  fun indexPrisoners(prisonerNumbers: List<String>) {
    webTestClient.put().uri("/prisoner-index/build-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    // wait for last offender to be available
    await.atMost(Duration.ofSeconds(60)) untilCallTo { prisonRequestCountFor("/api/offenders/${prisonerNumbers.last()}") } matches { it == 1 }
    await.atMost(Duration.ofSeconds(60)) untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
  }

  @Test
  fun `booking ids search returns bad request when no ids provided`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue("""{ "bookingIds":[] }"""))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 BookingIds")
  }

  @Test
  fun `booking ids search returns bad request when over 1000 prison numbers provided`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds((1..1001L).toList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 BookingIds")
  }

  @Test
  fun `booking ids search returns offender records, single result`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue("""{"bookingIds":[2]}"""))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN2")
  }

  @Test
  fun `ids search returns offender records, ignoring not found ids`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds(listOf(2L, 300L, 400L)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].bookingId").isEqualTo(2)
  }

  @Test
  fun `ids search can return over 10 hits (default max hits is 10)`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds((0..12L).toList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(12)
  }

  @Test
  fun `access forbidden for endpoint POST #prisoner-search#booking-ids when no role`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds(listOf(1)))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  private fun getOffenderBookingJson(offenderNo: String, bookingId: Long): String? {
    val templateOffender = gson.fromJson("/templates/booking.json".readResourceAsText(), OffenderBooking::class.java)
    return gson.toJson(templateOffender.copy(offenderNo = offenderNo, bookingId = bookingId))
  }
}

private fun String.readResourceAsText() =
  PrisonerSearchByPrisonerNumbersResourceTest::class.java.getResource(this).readText()
