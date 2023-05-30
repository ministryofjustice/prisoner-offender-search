package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.NotFoundException
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern

@RestController
@Validated
@RequestMapping("/prisoner-index", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Elastic Search index maintenance")
class PrisonerIndexResource(
  private val prisonerIndexService: PrisonerIndexService,
) {

  @PutMapping("/build-index")
  @Operation(
    summary = "Start building a new index",
    description = "Old index is left untouched and will be maintained whilst new index is built, requires PRISONER_INDEX role",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "409", description = "Unable to build index - it is marked as in progress or in error"),
    ],
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  fun buildIndex() = prisonerIndexService.buildIndex()

  @PutMapping("/cancel-index")
  @Operation(
    summary = "Cancels a building index",
    description = "Only cancels if indexing is in progress, requires PRISONER_INDEX role",
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  fun cancelIndex() = prisonerIndexService.cancelIndex()

  @PutMapping("/mark-complete")
  @Operation(
    summary = "Mark index as complete and swap",
    description = "Swaps to the newly built index, requires PRISONER_INDEX role",
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "409", description = "Unable to marked index complete as it is in error"),
    ],
  )
  fun indexComplete(@RequestParam(name = "ignoreThreshold", required = false) ignoreThreshold: Boolean = false) = prisonerIndexService.indexingComplete(ignoreThreshold)

  @PutMapping("/switch-index")
  @Operation(
    summary = "Switch index without rebuilding",
    description = "current index will be switched both indexed have to be complete, requires PRISONER_INDEX role",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "409", description = "Unable to switch indexes - one is marked as in progress or in error"),
    ],
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  fun switchIndex() = prisonerIndexService.switchIndex()

  @PutMapping("/index/prisoner/{prisonerNumber}")
  @Operation(
    summary = "Index/Refresh Data for Prisoner with specified prisoner Number",
    description = "Requires PRISONER_INDEX role",
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  fun indexPrisoner(
    @Parameter(
      required = true,
      example = "A1234AA",
    )
    @NotNull
    @Pattern(regexp = "[a-zA-Z][0-9]{4}[a-zA-Z]{2}")
    @PathVariable("prisonerNumber")
    prisonerNumber: String,
  ): Prisoner {
    val indexedPrisoner = prisonerIndexService.syncPrisoner(prisonerNumber)
    return indexedPrisoner.takeIf { it != null } ?: throw NotFoundException("$prisonerNumber not found")
  }

  @PutMapping("/queue-housekeeping")
  @Operation(
    summary = "Performs automated housekeeping tasks such as marking builds completed",
    description = "This is an internal service which isn't exposed to the outside world. It is called from a Kubernetes CronJob named `index-housekeeping-cronjob`",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "409", description = "Unable to marked index complete as it is in error"),
    ],
  )
  fun indexQueueHousekeeping() {
    prisonerIndexService.indexingComplete(ignoreThreshold = false)
  }

  @GetMapping("/compare-index")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Tag(name = "Elastic Search index comparison, async endpoint with results sent to a custom event called POSIndexReport. Requires ROLE_PRISONER_INDEX.")
  fun compareIndex() {
    prisonerIndexService.doCompare()
  }

  @GetMapping("/reconcile-index")
  @Operation(
    summary = "Start a full index comparison",
    description = """The whole existing index is compared in detail with current Nomis data, requires ROLE_PRISONER_INDEX.
      Results are written as customEvents. Nothing is written where a prisoner's data matches.
      Note this is a heavyweight operation, like a full index rebuild""",
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun startIndexReconciliation() = prisonerIndexService.startIndexReconciliation()

  @GetMapping("/reconcile-prisoner/{prisonerNumber}")
  @Operation(
    summary = "Compare a prisoner's index with Nomis",
    description = """Existing index is compared in detail with current Nomis data for a specific prisoner,
      with the index value coming first, Nomis second in the returned details. Requires ROLE_PRISONER_INDEX.""",
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  fun reconcilePrisoner(
    @Pattern(regexp = "[a-zA-Z][0-9]{4}[a-zA-Z]{2}")
    @PathVariable("prisonerNumber")
    prisonerNumber: String,
  ): String = prisonerIndexService.comparePrisonerDetail(prisonerNumber).toString()
}
