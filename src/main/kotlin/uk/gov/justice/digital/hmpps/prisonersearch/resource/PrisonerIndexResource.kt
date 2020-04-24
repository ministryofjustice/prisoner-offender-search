package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexQueueService
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType
import javax.validation.constraints.Size

@RestController
@Validated
@RequestMapping("/prisoner-index", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerIndexResource(val indexQueueService: IndexQueueService){

    @GetMapping("/prison/{prisonId}/activeOnly")
    @ApiOperation(value = "Index active prisoners in a specified prison")
    @PreAuthorize("hasAnyRole('PRISONER_INDEX','SYSTEM_USER')")
    fun indexActivePrisonersInPrison(@ApiParam("prisonId", example = "MDI") @PathVariable @Size(max = 3) prisonId: String): Map<String, String> {
        indexQueueService.sendIndexRequestMessage(IndexRequest(IndexRequestType.PRISON, prisonId))
        return mapOf("requested" to "true")
    }

    @GetMapping("/rebuild")
    @ApiOperation(value = "Index everything")
    @PreAuthorize("hasAnyRole('PRISONER_INDEX','SYSTEM_USER')")
    fun rebuild(): Map<String, String> {
        indexQueueService.sendIndexRequestMessage(IndexRequest(IndexRequestType.REBUILD, null))
        return mapOf("requested" to "true")
    }

}