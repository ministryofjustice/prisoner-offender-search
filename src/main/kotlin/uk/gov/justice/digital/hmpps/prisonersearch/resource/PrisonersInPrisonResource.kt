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
    @PathVariable("prisonId") @Parameter(required = true) prisonId: String,
    @RequestParam(value = "term", required = false, defaultValue = "") @Parameter term: String,
    @RequestParam(value = "page", required = false, defaultValue = "0") @Parameter page: Int,
    @RequestParam(value = "size", required = false, defaultValue = "10") @Parameter size: Int,
    @RequestParam(value = "alerts", required = false, defaultValue = "") @Parameter alerts: List<String>,
  ): Page<Prisoner> = searchService.search(
    prisonId,
    PrisonersInPrisonRequest(
      term = term,
      pagination = PaginationRequest(page, size),
      alertCodes = alerts
    )
  )
}
