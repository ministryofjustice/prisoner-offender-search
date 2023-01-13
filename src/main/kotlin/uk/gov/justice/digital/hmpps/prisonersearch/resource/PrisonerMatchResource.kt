package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.services.MatchService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerMatches

@RestController
@Validated
@RequestMapping(value = ["/match-prisoners"], produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH','ROLE_PRISONER_SEARCH')")
class PrisonerMatchResource(private val matchService: MatchService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Operation(
    summary = "Match for an prisoner by criteria. This is a more lenient version to other match endpoints that includes alias and fuzzy date of birth matching. It will return the best group of matching prisoners based on the request",
    description = "Specify the request criteria to match against, role required is ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH",
    security = [SecurityRequirement(name = "ROLE_GLOBAL_SEARCH"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = MatchRequest::class)
        )
      ]
    ),

    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = PrisonerMatches::class))]
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
  @Tag(name = "Deprecated")
  @PostMapping
  fun matchPrisoners(@Valid @RequestBody matchRequest: MatchRequest): PrisonerMatches {
    log.info("Match called with {}", matchRequest)
    return matchService.match(matchRequest)
  }
}
