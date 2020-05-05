package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.format.annotation.DateTimeFormat.ISO.DATE
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerSearchService
import java.time.LocalDate

@RestController
@Validated
@RequestMapping("/prisoner-search", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerSearchResource(val prisonerSearchService: PrisonerSearchService){

    @GetMapping("/find-by/id/{id}")
    @ApiOperation(value = "Find by IDs")
    @PreAuthorize("hasRole('GLOBAL_SEARCH')")
    fun findByPrisonerId(@ApiParam("id", example = "A1234AA", required = true) @PathVariable id: String): Prisoner? {
        return prisonerSearchService.findById(id)
    }

    @GetMapping("/find-by/bookingId/{id}")
    @ApiOperation(value = "Find by booking ID")
    @PreAuthorize("hasRole('GLOBAL_SEARCH')")
    fun findByBookingId(@ApiParam("id", example = "1241242", required = true) @PathVariable id: Long): Prisoner? {
        return prisonerSearchService.findByBookingId(id)
    }

    @GetMapping("/find-by/date-of-birth/{dateOfBirth}")
    @ApiOperation(value = "Find offenders with specified date of birth")
    @PreAuthorize("hasRole('GLOBAL_SEARCH')")
    fun findByDob(@ApiParam("dateOfBirth", required = true) @DateTimeFormat(iso = DATE) @PathVariable dateOfBirth: LocalDate,
                  @PageableDefault pageable : Pageable
    ): Page<Prisoner> {
        return prisonerSearchService.findByDob(dateOfBirth, pageable)
    }

    @GetMapping("/match/{keywords}")
    @ApiOperation(value = "Match offenders by keywords")
    @PreAuthorize("hasRole('GLOBAL_SEARCH')")
    fun findByKeywords(@ApiParam("keywords", example = "John Smith", required = true) @PathVariable keywords: String,
                       @ApiParam("prisonId", example = "MDI", required = false) @RequestParam(value = "prisonId", required = false) prisonId: String?,
                       @PageableDefault pageable : Pageable
    ) : Page<Prisoner> {
        if (prisonId != null) {
            return prisonerSearchService.findByKeywordsFilterByPrison(keywords, prisonId, pageable)
        }
        return prisonerSearchService.findByKeywords(keywords, pageable)
    }

    @GetMapping("/find-by/prison/{prisonId}")
    @ApiOperation(value = "Match offenders by prison")
    @PreAuthorize("hasRole('GLOBAL_SEARCH')")
    fun findByPrisonId(@ApiParam("prisonId", example = "MDI", required = true) @PathVariable prisonId: String,
                       @PageableDefault pageable : Pageable
    ) : Page<Prisoner> {
        return prisonerSearchService.findByPrisonId(prisonId, pageable)
    }

}