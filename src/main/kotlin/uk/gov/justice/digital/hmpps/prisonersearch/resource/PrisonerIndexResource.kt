package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService

@RestController
@Validated
@RequestMapping("/prisoner-index", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerIndexResource(val prisonerIndexService: PrisonerIndexService){

    @PutMapping("/build-index")
    @ApiOperation(value = "Start building a new index.", notes = "Old index is left untouched and will be maintained whilst new index is built")
    @PreAuthorize("hasRole('PRISONER_INDEX')")
    fun buildIndex(): IndexStatus {
        return prisonerIndexService.buildIndex()
    }

    @PutMapping("/mark-complete")
    @ApiOperation(value = "Mark index as complete and swap", notes = "Swaps to the newly built index")
    @PreAuthorize("hasRole('PRISONER_INDEX')")
    fun indexComplete(): IndexStatus{
        return prisonerIndexService.indexingComplete()
    }

}