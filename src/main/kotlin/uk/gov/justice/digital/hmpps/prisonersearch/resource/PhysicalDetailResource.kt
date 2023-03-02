package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
import uk.gov.justice.digital.hmpps.prisonersearch.services.PhysicalDetailService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PhysicalDetailRequest
import javax.validation.Valid

@RestController
@Validated
@RequestMapping(value = ["/physical-detail"], produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@Tag(name = "Experimental")
class PhysicalDetailResource(private val physicalDetailService: PhysicalDetailService) {
  // A hack to allow swagger to determine the response schema with a generic content
  abstract class PhysicalDetailResponse : Page<Prisoner>

  @Operation(
    summary = "*** BETA *** Physical details search for prisoners within a prison / group of prisons - returns a paginated result set",
    description = """
      BETA endpoint - physical details are not currently re-indexed if they change so results will be out of date / incorrect.
      Search by physical details.
      If a cell location is provided then only one prison can be supplied, otherwise multiple prisons are allowed.
      If lenient is set to false (default) then all supplied physical details must match in order for results to be returned.
      If lenient is set to true then at least one physical detail must match.
      Searches will return results for partial string matches, so searching for an ethnicity of white will return all
      prisoners with ethnicity of White: Eng./Welsh/Scot./N.Irish/British, White: Irish etc.
      Results are ordered so that prisoners that match the most criteria are returned first, then secondary order is by
      prisoner number.
      Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
      """,
    security = [SecurityRequirement(name = "ROLE_GLOBAL_SEARCH"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = PhysicalDetailRequest::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = PhysicalDetailResponse::class))],
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
  @PostMapping
  @Tag(name = "Physical detail search")
  fun prisonerDetailSearch(
    @Valid @RequestBody
    physicalDetailRequest: PhysicalDetailRequest,
  ): Page<Prisoner> = physicalDetailService.findByPhysicalDetail(physicalDetailRequest)
}
