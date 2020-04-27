package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
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
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerSearchService
import java.time.LocalDate

@RestController
@Validated
@RequestMapping("/prisoner-search", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerSearchResource(val prisonerSearchService: PrisonerSearchService){

    @GetMapping("/find-by/id/{id}")
    @ApiOperation(value = "Find by IDs")
    @PreAuthorize("hasAnyRole('GLOBAL_SEARCH','SYSTEM_USER')")
    fun findByPrisonerId(@ApiParam("id", example = "A1234AA") @PathVariable id: String): Prisoner? {
        return prisonerSearchService.findById(id)
    }

    @GetMapping("/find-by/date-of-birth/{dateOfBirth}")
    @ApiOperation(value = "Find offenders with specified date of birth")
    @PreAuthorize("hasAnyRole('GLOBAL_SEARCH','SYSTEM_USER')")
    fun findByDob(@ApiParam("dateOfBirth", required = true) @DateTimeFormat(iso = DATE) @PathVariable dateOfBirth: LocalDate
    ): Page<Prisoner> {
        return prisonerSearchService.findByDob(dateOfBirth)
    }

    @GetMapping("/match/{keywords}")
    @ApiOperation(value = "Match offenders by keywords")
    @PreAuthorize("hasAnyRole('GLOBAL_SEARCH','SYSTEM_USER')")
    fun findByKeywords(@ApiParam("keywords", example = "John Smith") @PathVariable keywords: String
    ): Page<Prisoner> {
        return prisonerSearchService.findByKeywords(keywords)
    }

}