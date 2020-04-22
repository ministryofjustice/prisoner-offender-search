package uk.gov.justice.digital.hmpps.prisonersearch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrisonerOffenderSearch

fun main(args: Array<String>) {
    runApplication<PrisonerOffenderSearch>(*args)
}