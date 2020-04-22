package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import io.swagger.annotations.Authorization
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import javax.validation.constraints.Size

@RestController
@Validated
@RequestMapping("/prisoner-index", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerIndexResource(val prisonerIndexService: PrisonerIndexService){

    @GetMapping("/prison/{prisonId}/activeOnly")
    @ApiOperation(value = "Index everything", authorizations = [Authorization("PRISONER_INDEX")])
    @PreAuthorize("hasAnyRole('PRISONER_INDEX','SYSTEM_USER')")
    fun indexActivePrisonersInPrison(@ApiParam("prisonId", example = "MDI") @PathVariable @Size(max = 3) prisonId: String): Map<String, String> {
        val numberIndexed = prisonerIndexService.indexActivePrisonersInPrison(prisonId)
        return mapOf("number-indexed" to numberIndexed.toString())
    }

    @GetMapping("/rebuild")
    @ApiOperation(value = "Index everything", authorizations = [Authorization("PRISONER_INDEX")])
    @PreAuthorize("hasAnyRole('PRISONER_INDEX','SYSTEM_USER')")
    fun rebuild(): Map<String, String> {
        val numberIndexed = prisonerIndexService.indexAll()
        return mapOf("number-indexed" to numberIndexed.toString())
    }

}