package uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

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

class IncentivesMockServer : WireMockServer(8096) {
  fun stubCurrentIncentive(
    iepLevel: String = "Standard",
    iepDate: String = "2022-11-10",
    iepTime: String = "2022-11-10T15:47:24.682335",
    nextReviewDate: String = "2023-11-18",
    daysSinceReview: Long = 120,
  ) {
    stubFor(
      get(urlPathMatching("/iep/reviews/booking/\\d+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(
              """
              {
                  "id": 5850394,
                  "iepLevel": "$iepLevel",
                  "prisonerNumber": "A9412DY",
                  "bookingId": 1203242,
                  "iepDate": "$iepDate",
                  "iepTime": "$iepTime",
                  "locationId": "RECP",
                  "iepDetails": [],
                  "nextReviewDate": "$nextReviewDate",
                  "daysSinceReview": $daysSinceReview
              }
              """.trimIndent()
            )
        )
    )
  }

  fun verifyGetCurrentIncentiveRequest(bookingId: Long) {
    verify(
      getRequestedFor(urlEqualTo("/iep/reviews/booking/$bookingId?with-details=false"))
    )
  }
}

class IncentivesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val incentivesApi = IncentivesMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    incentivesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    incentivesApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    incentivesApi.stop()
  }
}

class HmppsAuthApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val hmppsAuth = OAuthMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmppsAuth.start()
    hmppsAuth.stubGrantToken()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmppsAuth.resetRequests()
    hmppsAuth.stubGrantToken()
  }

  override fun afterAll(context: ExtensionContext) {
    hmppsAuth.stop()
  }
}
