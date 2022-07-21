package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonersInPrisonService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonersInPrisonRequest
import java.time.LocalDate

@RestController
@Validated
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
class PrisonersInPrisonResource(private val searchService: PrisonersInPrisonService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Operation(
    summary = "Search for prisoners within a particular prison establishment",
    description = """ 
      This search is optimised for clients that have a simple search term typically containing the prisonser's name
      or prisoner number. The user typically is certain the prisoner is within the establishment and knows key information 
      about the prisoner.
      Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
      """,
    security = [SecurityRequirement(name = "ROLE_GLOBAL_SEARCH"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = PrisonersInPrisonRequest::class)
        )
      ]
    ),

    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect information provided to perform prisoner match",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to search for prisoner data",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
    ]
  )
  @GetMapping("/prison/{prisonId}/prisoners", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun search(
    @PathVariable("prisonId") @Parameter(required = true)
    prisonId: String,
    @RequestParam(value = "term", required = false, defaultValue = "")
    @Parameter(description = "The primary search term. Whe absent all prisoners will be returned at the prison", example = "john smith")
    term: String,
    @RequestParam(value = "page", required = false, defaultValue = "0")
    @Parameter(description = "zero based page number to return")
    page: Int,
    @RequestParam(value = "size", required = false, defaultValue = "10")
    @Parameter(description = "number of items in each page of results")
    size: Int,
    @RequestParam(value = "alerts", required = false, defaultValue = "")
    @Parameter(description = "alert codes to filter by. Zero or more can be supplied. When multiple supplied the filter is effectively and OR", example = "XTACT")
    alerts: List<String>,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Parameter(description = "Offenders with a DOB >= this date", example = "1970-01-02")
    fromDob: LocalDate?,
    @RequestParam(value = "toDob", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Parameter(description = "Offenders with a DOB <= this date", example = "1975-01-02")
    toDob: LocalDate?,
    @RequestParam(value = "cellLocationPrefix", required = false)
    @Parameter(description = "Filter for the prisoners cell location. A block wing or cell can be specified. With prison id can be included or absent so HEI-3-1 and 3-1 are equivalent when the prison id is HEI", example = "3-1")
    cellLocationPrefix: String?,
  ): Page<Prisoner> = searchService.search(
    prisonId,
    PrisonersInPrisonRequest(
      term = term,
      pagination = PaginationRequest(page, size),
      alertCodes = alerts,
      fromDob = fromDob,
      toDob = toDob,
      cellLocationPrefix = cellLocationPrefix,
    )
  )
}
