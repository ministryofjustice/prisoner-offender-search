package uk.org.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import io.swagger.annotations.Authorization
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.format.annotation.DateTimeFormat.ISO.DATE
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.org.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.org.justice.digital.hmpps.prisonersearch.services.PrisonerSearchService
import java.time.LocalDate
import javax.validation.constraints.Size

@RestController
@Validated
@RequestMapping("/prisoner-search", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerSearchResource(val prisonerSearchService: PrisonerSearchService){

    @GetMapping("/findBy/prisonerId/{prisonerId}")
    @ApiOperation(value = "Find by Prisoner Id", authorizations = [Authorization("GLOBAL_SEARCH")])
    @PreAuthorize("hasAnyRole('GLOBAL_SEARCH','SYSTEM_USER')")
    fun findByPrisonerId(@ApiParam("Prisoner Id", example = "A1234AA") @PathVariable @Size(max = 7) prisonerId: String): Prisoner {
        return prisonerSearchService.findByPrisonerId(prisonerId)
    }

    @GetMapping("/keywords/{keywords}")
    @ApiOperation(value = "Find by Keywords", authorizations = [Authorization("GLOBAL_SEARCH")])
    @PreAuthorize("hasAnyRole('GLOBAL_SEARCH','SYSTEM_USER')")
    fun findByKeywords(@ApiParam("keywords", example = "J Smith") @PathVariable keywords: String
    ): Page<Prisoner> {
        return prisonerSearchService.findByKeywords(keywords)
    }

    @GetMapping("/dob/{dateOfBirth}")
    @ApiOperation(value = "Find by Names", authorizations = [Authorization("GLOBAL_SEARCH")])
    @PreAuthorize("hasAnyRole('GLOBAL_SEARCH','SYSTEM_USER')")
    fun findByDob(@ApiParam("dateOfBirth", required = true) @DateTimeFormat(iso = DATE) @PathVariable dateOfBirth: LocalDate
    ): Page<Prisoner> {
        return prisonerSearchService.findByDob(dateOfBirth)
    }
}