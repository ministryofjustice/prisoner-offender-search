package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerDifferencesService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerDetailRequest
import java.time.Instant

@RestController
@Validated
@RequestMapping(value = ["/prisoner-differences"], produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('PRISONER_INDEX')")
class PrisonerDifferencesResource(private val prisonerDifferencesService: PrisonerDifferencesService) {

  @Operation(
    summary = "Find all prisoner differences",
    description = """
      Find all prisoner differences since a given date time.  This defaults to within the last 24 hours.
      Requires PRISONER_INDEX role.
      """,
    security = [SecurityRequirement(name = "PRISONER_INDEX")],
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = PrisonerDetailRequest::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect information provided to perform prisoner match",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to search for prisoner data",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping
  fun prisonerDifferences(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(description = "Report on differences that have been generated. Defaults to the last 24 hours", example = "2023-01-02T02:23:45")
    from: Instant?,
    to: Instant?,
  ): List<PrisonerDifferences> =
    prisonerDifferencesService.retrieveDifferences(from ?: Instant.now().minusSeconds(60 * 60 * 24), to ?: Instant.now())
}
