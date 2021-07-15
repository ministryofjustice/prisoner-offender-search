package uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders

class PrisonMockServer : WireMockServer(8093)

class OAuthMockServer : WireMockServer(8090) {

  fun stubGrantToken() {
    stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """
              {
                 "access_token": "ABCDE", 
                 "token_type": "bearer"
              }
              """.trimIndent()
            )
        )
    )
  }
}

class RestrictedPatientMockServer : WireMockServer(8095) {
  fun verifyGetRestrictedPatientRequest(prisonerNumber: String) {
    verify(
      getRequestedFor(urlEqualTo("/restricted-patient/prison-number/$prisonerNumber"))
    )
  }
}
