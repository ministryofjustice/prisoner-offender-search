@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonersearch.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerB
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import java.time.LocalDate

class PrisonerIndexResource_compareIndexTest : AbstractSearchDataIntegrationTest() {

  @Autowired
  lateinit var prisonerBRepository: PrisonerBRepository

  @Test
  fun `access forbidden when no authority`() {
    webTestClient.get().uri("/prisoner-index/compare-index")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.get().uri("/prisoner-index/compare-index")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `Diffs reported`() {
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/ids"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withHeader("Total-Records", "10")
            .withBody(
              """[
                { "offenderNumber": "A9999AA" },
                { "offenderNumber": "A9999AB" },
                { "offenderNumber": "A9999AC" },
                { "offenderNumber": "A9999RA" },
                { "offenderNumber": "A9999RB" },
                { "offenderNumber": "A9999RC" },
                { "offenderNumber": "A7089EY" },
                { "offenderNumber": "A7089EZ" },
                { "offenderNumber": "A1234SR" },
                { "offenderNumber": "A7089FA" }]""",
            ),
        ),
    )
    webTestClient.get().uri("/prisoner-index/compare-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isAccepted

    verify(telemetryClient, timeout(2000).atLeastOnce()).trackEvent(
      eq("POSIndexReport"),
      check<Map<String, String>> {
        assertThat(it["onlyInIndex"]).isEqualTo("[A1090AA, A7089FB, A7089FC, A7089FX, A7090AA, A7090AB, A7090AC, A7090AD, A7090AE, A7090AF, A7090BA, A7090BB, A7090BC, A7090BD, A7090BE, A7090BF]")
        assertThat(it["onlyInNomis"]).isEqualTo("[A1234SR]")
        assertThat(it["timeMs"]?.toInt()).isGreaterThan(0)
      },
      isNull(),
    )
  }

  @Test
  fun `Reconciliation - no differences`() {
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/ids"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withHeader("Total-Records", "10")
            .withBody(
              """[
                { "offenderNumber": "A9999AA" },
                { "offenderNumber": "A9999AB" },
                { "offenderNumber": "A9999AC" },
                { "offenderNumber": "A9999RA" },
                { "offenderNumber": "A9999RB" },
                { "offenderNumber": "A9999RC" },
                { "offenderNumber": "A7089EY" },
                { "offenderNumber": "A7089EZ" },
                { "offenderNumber": "A7089FA" }]""",
            ),
        ),
    )
    webTestClient.get().uri("/prisoner-index/reconcile-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isAccepted

    verifyNoInteractions(telemetryClient)
  }

  @Test
  fun `Reconciliation - differences`() {
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/ids"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withHeader("Total-Records", "10")
            .withBody(
              """[
                { "offenderNumber": "A9999AA" },
                { "offenderNumber": "A9999AB" },
                { "offenderNumber": "A9999AC" },
                { "offenderNumber": "A9999RA" },
                { "offenderNumber": "A9999RB" },
                { "offenderNumber": "A9999RC" },
                { "offenderNumber": "A7089EY" },
                { "offenderNumber": "A7089EZ" },
                { "offenderNumber": "A7089FA" }]""",
            ),
        ),
    )
    // Modify index record A9999AA a little
    val record1 = prisonerBRepository.findByIdOrNull("A9999AA")!!
    record1.releaseDate = LocalDate.parse("2023-01-02")
    prisonerBRepository.save(record1)

    // Modify index record A7089EY a lot
    val record2 = PrisonerB()
    record2.prisonerNumber = "A7089EY"
    prisonerBRepository.save(record2)

    webTestClient.get().uri("/prisoner-index/reconcile-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isAccepted

    verify(telemetryClient, timeout(5000)).trackEvent(
      eq("POSPrisonerDifferenceReported"),
      check<Map<String, String>> {
        assertThat(it["nomsNumber"]).isEqualTo("A9999AA")
        assertThat(it["categoriesChanged"]).isEqualTo("[SENTENCE]")
      },
      isNull(),
    )

    val detailsForA9999AA = webTestClient.get().uri("/prisoner-index/reconcile-prisoner/A9999AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .returnResult().responseBody

    assertThat(detailsForA9999AA).isEqualTo("""[[releaseDate: 2023-01-02, null]]""")

    verify(telemetryClient, timeout(2000)).trackEvent(
      eq("POSPrisonerDifferenceReported"),
      check<Map<String, String>> {
        assertThat(it["nomsNumber"]).isEqualTo("A7089EY")
        assertThat(it["categoriesChanged"]).isEqualTo("[ALERTS, IDENTIFIERS, LOCATION, PERSONAL_DETAILS, PHYSICAL_DETAILS, SENTENCE, STATUS]")
      },
      isNull(),
    )

    val detailsForA7089EY = webTestClient.get().uri("/prisoner-index/reconcile-prisoner/A7089EY")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .returnResult().responseBody

    assertThat(detailsForA7089EY).contains(
      "[active: false, true]",
      "[bookingId: null, 1900836]",
      "[alerts: null, [PrisonerAlert(alertType=P, alertCode=PL1, active=true, expired=false),",
      "[nonDtoReleaseDate: null, 2023-05-16]",
    )

    verify(telemetryClient, never()).trackEvent(
      eq("POSPrisonerDifferenceReported"),
      check<Map<String, String>> {
        assertThat(it["nomsNumber"]).isEqualTo("A9999AB")
      },
      isNull(),
    )

    resetSearchData()
  }
}
