package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springdoc.api.annotations.ParameterObject
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.GlobalSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.NotFoundException

@RestController
@Validated
class GlobalSearchResource(
  private val globalSearchService: GlobalSearchService,
  private val prisonerIndexService: PrisonerIndexService,
  private val telemetryClient: TelemetryClient
) {

  @PostMapping(
    "/global-search",
    produces = [MediaType.APPLICATION_JSON_VALUE],
    consumes = [MediaType.APPLICATION_JSON_VALUE]
  )
  @PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
  @Operation(summary = "Match prisoners by criteria", description = "Requires ROLE_GLOBAL_SEARCH role or ROLE_PRISONER_SEARCH role")
  fun globalFindByCriteria(
    @RequestBody globalSearchCriteria: GlobalSearchCriteria,
    @ParameterObject @PageableDefault pageable: Pageable
  ) = globalSearchService.findByGlobalSearchCriteria(globalSearchCriteria, pageable)

  @GetMapping("/prisoner/{id}")
  @PreAuthorize("hasAnyRole('ROLE_VIEW_PRISONER_DATA', 'ROLE_PRISONER_SEARCH')")
  @Operation(
    summary = "Get prisoner by Id",
    description = "Requires ROLE_PRISONER_SEARCH or ROLE_VIEW_PRISONER_DATA role",
    security = [SecurityRequirement(name = "ROLE_VIEW_PRISONER_DATA"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],
  )
  fun findByPrison(@PathVariable id: String) = prisonerIndexService.get(id).takeIf { it != null } ?: throw NotFoundException("$id not found")

  @GetMapping("/synthetic-monitor")
  fun syntheticMonitor() {
    val start = System.currentTimeMillis()
    val results = globalSearchService.findByGlobalSearchCriteria(
      GlobalSearchCriteria(
        prisonerIdentifier = null,
        firstName = null,
        lastName = "Smith",
        gender = null,
        location = null
      ),
      PageRequest.of(0, 10)
    )
    telemetryClient.trackEvent("synthetic-monitor", mapOf("results" to "${results.totalElements}", "timeMs" to (System.currentTimeMillis() - start).toString()), null)
  }
}
