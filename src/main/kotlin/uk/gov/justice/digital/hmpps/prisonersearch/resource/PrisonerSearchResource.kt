package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria

@RestController
@Validated
@RequestMapping("/prisoner-search", produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerSearchResource(val prisonerSearchService: PrisonerSearchService){

    @PostMapping("/match")
    @ApiOperation(value = "Match prisoners by criteria", notes = "Requires GLOBAL_SEARCH role")
    @PreAuthorize("hasRole('GLOBAL_SEARCH')")
    fun findByCriteria(@ApiParam(required = true, name = "searchCriteria") @RequestBody searchCriteria : SearchCriteria
    ) : List<Prisoner> {
        return prisonerSearchService.findBySearchCriteria(searchCriteria)
    }

    @PostMapping("/prisoner-numbers")
    @ApiOperation(value = "Match prisoners by a list of prisoner numbers", notes = "Requires GLOBAL_SEARCH role")
    @PreAuthorize("hasRole('GLOBAL_SEARCH')")
    fun findByIds(@ApiParam(required = true, name = "prisonerNumberList") @RequestBody prisonerNumberList : PrisonerListCriteria
    ) : List<Prisoner> {
      return prisonerSearchService.findByListOfPrisonerNumbers(prisonerNumberList)
    }

}
