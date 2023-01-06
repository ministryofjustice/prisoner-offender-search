package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springdoc.api.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonersInPrisonService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonersInPrisonRequest
import java.time.LocalDate

@RestController
@Validated
@PreAuthorize("hasAnyRole('ROLE_PRISONER_IN_PRISON_SEARCH', 'ROLE_PRISONER_SEARCH')")
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
      
      Requires ROLE_PRISONER_IN_PRISON_SEARCH or ROLE_PRISONER_SEARCH role.
      
      Sort fields supported are: firstName, lastName, prisonerNumber, dateOfBirth, cellLocation e.g "sort=firstName,lastName,desc"
      
      Examples:
      
      "/prisoners-in-prison/BXI?term=John&sort=firstName,lastName,desc&page=2&size=20"
      This will return all people in HMP Brixton whose first or last names begins with JOHN. 
      Results will be ordered by firstName, lastName descending. 
      Page 3 will be returned with a maximum of 20 results per page.
      
      "/prisoners-in-prison/WWI?sort=cellLocation"
      This will return all people in HMP Wandsworth. 
      Results will be ordered by cell location ascending. 
      Page 1 will be returned with a maximum of 10 results per page.
      
      "/prisoners-in-prison/WWI?cellLocationPrefix=WWI-2&term=smith"
      "/prisoners-in-prison/WWI?cellLocationPrefix=2&term=smith"
      This will return all people in HMP Wandsworth block 2 whose name starts with SMITH. 

      "/prisoners-in-prison/WWI?cellLocationPrefix=2-A-3-001"
      This will return all people in HMP Wandsworth cell WWI-2-A-3-001 

      "/prisoners-in-prison/WWI?term=A1234KJ"
      "/prisoners-in-prison/WWI?term=A1234KJ bananas"
      This will return the single prisoner with prisoner number A1234KJ in HMP Wandsworth. 
      An empty page will be returned if not found

      "/prisoners-in-prison/WWI?term=A J&fromDob=1956-01-01&toDob=2000-01-02"
      This will return all people in HMP Wandsworth. Born on or after 1956-01-01 and on or before 2000-01-02, 
      whose name begins with A J, e.g Alan Jones born on 1956-01-01. 

      "/prisoners-in-prison/WWI?alerts=TACT&alerts=PEEP"
      This will return all people in HMP Wandsworth. With the alerts TACT or PEEP.

      """,
    security = [SecurityRequirement(name = "ROLE_PRISONER_IN_PRISON_SEARCH"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],

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
  @Tag(name = "Establishment search")
  @Tag(name = "Popular")
  fun search(
    @PathVariable("prisonId") @Parameter(required = true)
    prisonId: String,
    @RequestParam(value = "term", required = false, defaultValue = "")
    @Parameter(description = "The primary search term. Whe absent all prisoners will be returned at the prison", example = "john smith")
    term: String,
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
    @ParameterObject @PageableDefault(sort = ["lastName", "firstName", "prisonerNumber"], direction = Sort.Direction.ASC) pageable: Pageable
  ) = searchService.search(
    prisonId,
    PrisonersInPrisonRequest(
      term = term,
      pagination = PaginationRequest(pageable.pageNumber, pageable.pageSize),
      alertCodes = alerts,
      fromDob = fromDob,
      toDob = toDob,
      cellLocationPrefix = cellLocationPrefix,
      sort = pageable.sort
    )
  )
}
