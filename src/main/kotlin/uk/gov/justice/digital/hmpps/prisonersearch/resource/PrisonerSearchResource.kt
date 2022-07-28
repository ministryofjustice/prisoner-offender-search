package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.api.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonSearch
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.BookingIds
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.PrisonerNumbers
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.services.ReleaseDateSearch
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PossibleMatchCriteria
import javax.validation.Valid

@RestController
@Validated
@RequestMapping(
  "/prisoner-search",
  produces = [MediaType.APPLICATION_JSON_VALUE],
  consumes = [MediaType.APPLICATION_JSON_VALUE]
)
class PrisonerSearchResource(private val prisonerSearchService: PrisonerSearchService) {

  @Deprecated(message = "Use the /match-prisoners endpoint")
  @PostMapping("/match")
  @Operation(
    summary = "Match prisoners by criteria, to search across a list of specific prisons use /match-prisoners",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role"
  )
  @Tag(name = "Deprecated")
  fun findByCriteria(@Parameter(required = true) @RequestBody prisonSearch: PrisonSearch) =
    prisonerSearchService.findBySearchCriteria(prisonSearch.toSearchCriteria())

  @PostMapping("/match-prisoners")
  @Operation(summary = "Match prisoners by criteria, searching by prisoner identifier or name and returning results for the criteria matched first. Typically used when the matching data is of high quality where the first match is expected to be a near perfect match.", description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role")
  @Tag(name = "Matching")
  @Tag(name = "Popular")
  fun findByCriteria(@Parameter(required = true) @RequestBody searchCriteria: SearchCriteria) =
    prisonerSearchService.findBySearchCriteria(searchCriteria)

  @PostMapping("/possible-matches")
  @Operation(summary = "Search for possible matches by criteria, searching by prison number, PNC number, and/or name and date of birth, returning collated results by order of search. This will also search aliases for possible matches. Use when there is manual input, e.g. a user can select the correct match from search results.", description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role")
  @Tag(name = "Matching")
  fun findPossibleMatchesBySearchCriteria(@Parameter(required = true) @RequestBody searchCriteria: PossibleMatchCriteria) =
    prisonerSearchService.findPossibleMatchesBySearchCriteria(searchCriteria)

  @PostMapping("/prisoner-numbers")
  @Operation(summary = "Match prisoners by a list of prisoner numbers", description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role")
  @Tag(name = "Batch")
  @Tag(name = "Popular")
  fun findByNumbers(@Parameter(required = true) @Valid @RequestBody criteria: PrisonerNumbers) =
    prisonerSearchService.findBy(criteria)

  @PostMapping("/booking-ids")
  @Operation(summary = "Match prisoners by a list of booking ids", description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role")
  @Tag(name = "Batch")
  fun findByIds(@Parameter(required = true) @Valid @RequestBody criteria: BookingIds) =
    prisonerSearchService.findBy(criteria)

  @PostMapping("/release-date-by-prison")
  @Operation(
    summary = "Match prisoners who have a release date within a range, and optionally by prison",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role"
  )
  @Tag(name = "Batch")
  @Tag(name = "Specific use case")
  fun findByReleaseDateAndPrison(
    @Parameter(required = true) @Valid @RequestBody criteria: ReleaseDateSearch,
    @ParameterObject @PageableDefault pageable: Pageable
  ) = prisonerSearchService.findByReleaseDate(criteria, pageable)

  @GetMapping("/prison/{prisonId}")
  @Operation(summary = "Get all prisoners in a prison, including restricted patients supported by a POM", description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role")
  @Tag(name = "Batch")
  @Tag(name = "Popular")
  fun findByPrison(
    @Valid @PathVariable prisonId: String,
    @RequestParam("include-restricted-patients", required = false, defaultValue = "false") includeRestrictedPatients: Boolean,
    @ParameterObject @PageableDefault pageable: Pageable
  ) = prisonerSearchService.findByPrison(prisonId.uppercase(), pageable, includeRestrictedPatients)
}
