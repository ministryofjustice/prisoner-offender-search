package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonId
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria
import javax.validation.Valid

@RestController
@Validated
@RequestMapping(
    "/prisoner-search",
    produces = [MediaType.APPLICATION_JSON_VALUE],
    consumes = [MediaType.APPLICATION_JSON_VALUE]
)
class PrisonerSearchResource(private val prisonerSearchService: PrisonerSearchService) {

    @PostMapping("/match")
    @Operation(summary = "Match prisoners by criteria", description = "Requires GLOBAL_SEARCH role")
    fun findByCriteria(@Parameter(required = true) @RequestBody searchCriteria: SearchCriteria) =
        prisonerSearchService.findBySearchCriteria(searchCriteria)

    @PostMapping("/prisoner-numbers")
    @Operation(summary = "Match prisoners by a list of prisoner numbers", description = "Requires GLOBAL_SEARCH role")
    fun findByIds(@Parameter(required = true) @Valid @RequestBody prisonerNumberList: PrisonerListCriteria) =
        prisonerSearchService.findByListOfPrisonerNumbers(prisonerNumberList)

    @PostMapping("/prison")
    @Operation(summary = "Match prisoners by prison", description = "Requires GLOBAL_SEARCH role")
    fun findByPrison(
        @Valid @RequestBody prisonId: PrisonId,
        @PageableDefault pageable: Pageable
    ) = prisonerSearchService.findByPrison(prisonId, pageable)
}
