package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking

class PrisonerSearchByPrisonerNumbersResourceTest : QueueIntegrationTest() {

  companion object {
    var initialiseSearchData = true
  }

  data class IDs (val offenderNumber: String)

  @BeforeEach
  fun setup() {

      if (initialiseSearchData) {
        val prisonerNumbers = getTestPrisonerNumbers(12)
        prisonMockServer.stubFor(get(urlEqualTo("/api/offenders/ids"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withHeader("Total-Records", prisonerNumbers.size.toString())
            .withBody(gson.toJson( prisonerNumbers.map { IDs(it) }))))
        prisonerNumbers.forEach {
          prisonMockServer.stubFor(get(urlEqualTo("/api/offenders/$it"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(getOffenderBookingJson(it))))
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
    await untilCallTo { prisonRequestCountFor("/api/offenders/${prisonerNumbers.last()}") } matches { it == 1 }

    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
  }

  private fun getTestPrisonerNumbers(count: Int): List<String> {
    return List(count) { i -> "AN$i"  }
  }

  @Test
  fun `prisoner number search returns bad request when no prison numbers provided`() {

    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue("{\"prisonerNumbers\":[]}"))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage").isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 200 prisoner numbers")
  }

  @Test
  fun `prisoner number search returns bad request when over 200 prison numbers provided`() {

    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerListCriteria(getTestPrisonerNumbers(201)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage").isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 200 prisoner numbers")
  }

  @Test
  fun `prisoner number search returns offender records, single result`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerListCriteria(listOf("AN2")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN2")
  }

  @Test
  fun `prisoner number search returns offender records, ignoring not found prison numbers`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerListCriteria(listOf("AN2", "AN33", "AN44")))))
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
      .body(BodyInserters.fromValue(gson.toJson(PrisonerListCriteria(getTestPrisonerNumbers(12)))))
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
      .body(BodyInserters.fromValue(gson.toJson(PrisonerListCriteria(arrayListOf("ABC")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }


  private fun getOffenderBookingJson(offenderNo: String): String? {
    val templateOffender = gson.fromJson("/templates/booking.json".readResourceAsText(), OffenderBooking::class.java)
    return gson.toJson(templateOffender.copy(offenderNo = offenderNo))
  }
}


private fun String.readResourceAsText(): String = PrisonerSearchByPrisonerNumbersResourceTest::class.java.getResource(this).readText()



