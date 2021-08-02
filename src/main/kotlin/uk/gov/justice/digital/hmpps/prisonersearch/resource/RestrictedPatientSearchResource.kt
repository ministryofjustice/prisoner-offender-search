package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springdoc.api.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.services.RestrictedPatientSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.RestrictedPatientSearchService

@RestController
@Validated
@RequestMapping(
  "/restricted-patient-search",
  produces = [MediaType.APPLICATION_JSON_VALUE],
  consumes = [MediaType.APPLICATION_JSON_VALUE]
)
class RestrictedPatientSearchResource(private val restrictedPatientSearchService: RestrictedPatientSearchService) {

  @PostMapping("/match-restricted-patients")
  @Operation(summary = "Match prisoners by criteria", description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role")
  fun findByCriteria(
    @Parameter(required = true) @RequestBody searchCriteria: RestrictedPatientSearchCriteria,
    @ParameterObject @PageableDefault pageable: Pageable
  ) =
    restrictedPatientSearchService.findBySearchCriteria(searchCriteria, pageable)
}
