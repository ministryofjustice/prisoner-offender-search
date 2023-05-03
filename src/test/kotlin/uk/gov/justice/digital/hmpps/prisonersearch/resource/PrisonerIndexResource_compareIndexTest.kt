@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonersearch.AbstractSearchDataIntegrationTest

class PrisonerIndexResource_compareIndexTest : AbstractSearchDataIntegrationTest() {

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
}
