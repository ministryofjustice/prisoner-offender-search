package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictedPatientDto

@Service
@ConditionalOnProperty(value = ["api.base.url.restricted-patients"], havingValue = "true")
class RestrictedPatientService(@Qualifier("restrictedPatientsWebClient") val webClient: WebClient) {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? =
    webClient.get().uri("/restricted-patient/prison-number/$prisonerNumber")
      .retrieve()
      .bodyToMono(RestrictedPatientDto::class.java)
      .block()
}

@Service
@ConditionalOnProperty(value = ["api.base.url.restricted-patients"], havingValue = "false")
class StubRestrictedPatientService {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? = null
}
