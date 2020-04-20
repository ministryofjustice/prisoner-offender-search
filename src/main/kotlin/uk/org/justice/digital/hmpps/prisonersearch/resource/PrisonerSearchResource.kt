package uk.org.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import io.swagger.annotations.Authorization
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.org.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.org.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import java.time.LocalDate
import javax.validation.constraints.Size

@RestController
@Validated
@RequestMapping("/prisoner-search", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonerSearchResource(val prisonerIndexService: PrisonerIndexService){

    @GetMapping("/index")
    @ApiOperation(value = "Index everything", authorizations = [Authorization("SYSTEM_USER")])
    fun index(): Map<String, String> {

        val prisoner1 = Prisoner("A1234AA", 123, "B1213",
            "Mike", "Willis", LocalDate.of(1970,1,1), "MDI", true)
        val prisoner2 = Prisoner("A1234AB", 124, "B1214",
            "Rosie", "Willis", LocalDate.of(1998,8,28), "LPI", true)
        val prisoner3 = Prisoner("A1234AC", 125, "B1215",
            "Molly", "Willis", LocalDate.of(1999,10,27), "PVI", true)
        val prisoner4 = Prisoner("A1234AD", 126, "B1216",
            "David", "Symons", LocalDate.of(1970,6,4), "LEI", true)
        val prisoner5= Prisoner("A1234AE", 127, "B1217",
            "Trevor", "Smith", LocalDate.of(2003,6,4), "LEI", true)

        prisonerIndexService.save(prisoner1)
        prisonerIndexService.save(prisoner2)
        prisonerIndexService.save(prisoner3)
        prisonerIndexService.save(prisoner4)
        prisonerIndexService.save(prisoner5)
        return mapOf("Status" to "OK")
    }

    @GetMapping("/findBy/prisonerId/{prisonerId}")
    @ApiOperation(value = "Find by Prisoner Id", authorizations = [Authorization("SYSTEM_USER")])
    fun findByPrisonerId(@ApiParam("Prisoner Id", example = "A1234AA") @PathVariable @Size(max = 7) prisonerId: String): Prisoner {
        return prisonerIndexService.findByPrisonerId(prisonerId)
    }

    @GetMapping("/findBy/names/{lastName}/{firstName}")
    @ApiOperation(value = "Find by Names", authorizations = [Authorization("SYSTEM_USER")])
    fun findByNames(@ApiParam("Last Name", example = "Smith") @PathVariable lastName: String,
                    @ApiParam("First Name", example = "John") @PathVariable firstName: String
    ): Page<Prisoner> {
        return prisonerIndexService.findByLastAndFirstName(lastName, firstName)
    }

}