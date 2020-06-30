package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.NotFoundException
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern

@RestController
@Validated
@RequestMapping("/prisoner-index", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerIndexResource(val prisonerIndexService: PrisonerIndexService){

    @PutMapping("/build-index")
    @ApiOperation(value = "Start building a new index.", notes = "Old index is left untouched and will be maintained whilst new index is built, requires PRISONER_INDEX role")
    @PreAuthorize("hasRole('PRISONER_INDEX')")
    fun buildIndex(): IndexStatus {
        return prisonerIndexService.buildIndex()
    }

    @PutMapping("/cancel-index")
    @ApiOperation(value = "Cancels a building index.", notes = "Only cancels if indexing is in progress, requires PRISONER_INDEX role")
    @PreAuthorize("hasRole('PRISONER_INDEX')")
    fun cancelIndex(): IndexStatus {
        return prisonerIndexService.cancelIndex()
    }

    @PutMapping("/mark-complete")
    @ApiOperation(value = "Mark index as complete and swap", notes = "Swaps to the newly built index, requires PRISONER_INDEX role")
    @PreAuthorize("hasRole('PRISONER_INDEX')")
    fun indexComplete(): IndexStatus{
        return prisonerIndexService.indexingComplete()
    }

    @PutMapping("/index/prisoner/{prisonerNumber}")
    @ApiOperation(value = "Index/Refresh Data for Prisoner with specified prisoner Number", notes = "Requires PRISONER_INDEX role")
    @PreAuthorize("hasRole('PRISONER_INDEX')")
    fun indexPrisoner(@ApiParam(required = true, name = "prisonerNumber", example = "A1234AA") @NotNull @Pattern(regexp = "[a-zA-Z][0-9]{4}[a-zA-Z]{2}") @PathVariable("prisonerNumber") prisonerNumber: String): Prisoner {
        val indexedPrisoner = prisonerIndexService.indexPrisoner(prisonerNumber)
        return indexedPrisoner.takeIf{ it != null } ?: throw NotFoundException("$prisonerNumber not found")
    }
}
