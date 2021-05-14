package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.services.KeywordService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest
import javax.validation.Valid

@RestController
@Validated
@RequestMapping(value = ["/keyword"], produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_GLOBAL_SEARCH')")
class PrisonerKeywordResource(private val keywordService: KeywordService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Operation(
    summary = "Work in progress - find prisoners by keywords. It will return the best matching prisoners based on the request",
    description = "Specify the keywords to match against the indexed text for prisoners. Requires role GLOBAL_SEARCH",
    security = [SecurityRequirement(name = "GLOBAL_SEARCH")],
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = KeywordRequest::class)
        )
      ]
    ),

    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
        content = [Content(mediaType = "application/json")]
        // Will introspect the response type from the method calls
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
  @PostMapping
  fun keywordSearch(
    @Valid @RequestBody keywordRequest: KeywordRequest
  ): Page<Prisoner> = keywordService.findByKeyword(keywordRequest)
}
