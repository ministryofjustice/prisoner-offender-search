package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictedPatientDto

@Service
interface RestrictedPatientService {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto?
}

@Service
@ConditionalOnProperty(value = ["api.base.url.restricted-patients"])
class RestrictedPatientServiceImpl(@Qualifier("restrictedPatientsWebClient") val webClient: WebClient) :
  RestrictedPatientService {
  override fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? {
    try {
      return webClient.get().uri("/restricted-patient/prison-number/{prisonerNumber}", prisonerNumber)
        .retrieve()
        .bodyToMono(RestrictedPatientDto::class.java)
        .block()
    } catch (e: WebClientResponseException) {
      if (e.statusCode.equals(HttpStatus.NOT_FOUND)) return null

      throw e
    }
  }
}

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isBlank('\${api.base.url.restricted-patients:}')")
class StubRestrictedPatientService : RestrictedPatientService {
  override fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? = null
}
