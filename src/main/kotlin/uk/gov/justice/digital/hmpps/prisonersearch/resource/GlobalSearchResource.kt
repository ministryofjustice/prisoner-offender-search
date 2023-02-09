package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexStatusService
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.NotFoundException

@RestController
@Validated
class GlobalSearchResource(
  private val globalSearchService: GlobalSearchService,
  private val prisonerIndexService: PrisonerIndexService,
  private val indexStatusService: IndexStatusService,
  private val telemetryClient: TelemetryClient
) {

  @PostMapping(
    "/global-search",
    produces = [MediaType.APPLICATION_JSON_VALUE],
    consumes = [MediaType.APPLICATION_JSON_VALUE]
  )
  @PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
  @Operation(
    summary = "Match prisoners by criteria",
    description = "Requires ROLE_GLOBAL_SEARCH role or ROLE_PRISONER_SEARCH role"
  )
  @Tag(name = "Global search")
  @Tag(name = "Popular")
  fun globalFindByCriteria(
    @RequestBody globalSearchCriteria: GlobalSearchCriteria,
    @ParameterObject @PageableDefault pageable: Pageable
  ) = globalSearchService.findByGlobalSearchCriteria(globalSearchCriteria, pageable)

  @GetMapping("/prisoner/{id}")
  @PreAuthorize("hasAnyRole('ROLE_VIEW_PRISONER_DATA', 'ROLE_PRISONER_SEARCH')")
  @Operation(
    summary = "Get prisoner by prisoner number (AKA NOMS number)",
    description = "Requires ROLE_PRISONER_SEARCH or ROLE_VIEW_PRISONER_DATA role",
    security = [SecurityRequirement(name = "ROLE_VIEW_PRISONER_DATA"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],
  )
  @Tag(name = "Popular")
  fun findByPrisonNumber(@PathVariable id: String) =
    prisonerIndexService.get(id).takeIf { it != null } ?: throw NotFoundException("$id not found")

  @GetMapping("/synthetic-monitor")
  @Tag(name = "Elastic Search index maintenance")
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
    val mid = System.currentTimeMillis()
    val totalNomisNumber = getTotalNomisNumber()
    val totalIndexNumber = prisonerIndexService.countIndex(indexStatusService.getCurrentIndex().currentIndex)
    val end = System.currentTimeMillis()

    telemetryClient.trackEvent(
      "synthetic-monitor",
      mapOf(
        "results" to "${results.totalElements}",
        "timeMs" to (mid - start).toString(),
        "totalNomis" to totalNomisNumber.toString(),
        "totalIndex" to totalIndexNumber.toString(),
        "totalNumberTimeMs" to (end - mid).toString(),
      ),
      null
    )
  }

  @GetMapping("/compare-index")
  @PreAuthorize("hasRole('ROLE_PRISONER_SEARCH')")
  fun compareIndex() {
    val start = System.currentTimeMillis()
    val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()
    val end = System.currentTimeMillis()
    telemetryClient.trackEvent(
      "index-report",
      mapOf(
        "onlyInIndex" to toLogMessage(onlyInIndex),
        "onlyInNomis" to toLogMessage(onlyInNomis),
        "timeMs" to (end - start).toString(),
      ),
      null
    )
  }

  private val cutoff = 50

  private fun toLogMessage(onlyList: List<String>): String {
    return if (onlyList.size <= cutoff) onlyList.toString() else onlyList.slice(IntRange(0, cutoff)).toString() + "..."
  }

  private fun getTotalNomisNumber(): Int = prisonerIndexService.getAllNomisOffenders(0, 1).totalRows.toInt()
}
