package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.translate
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerRepository

@Service
class PrisonerIndexService(val nomisService: NomisService,
                           val prisonerRepository: PrisonerRepository) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun save(prisoner : Prisoner) : Prisoner {
        return prisonerRepository.save(prisoner)
    }

    //TODO: this is just a dummy way of indexing - just a tmp solution to try things out
    fun indexActivePrisonersInPrison(prisonId : String) : Int {
        log.debug("Indexing Active Prisoner in {}", prisonId)

        var count = 0
        nomisService.getOffendersByPrison(prisonId)?.forEach {
            prisonerRepository.save(translate(it))
            count += 1
        }
        log.debug("Indexed {} prisoners", count)
        return count
    }

    //TODO: this is just a dummy way of indexing - just a tmp solution to try things out
    fun indexAll() : Int {
        log.debug("Indexing All prisoners")

        var count = 0
        var offset = 0
        do {
            var pageCount = 0
            nomisService.getOffendersIds(offset, 100)?.forEach {
                nomisService.getOffender(it.offenderNumber)?.let { ob ->
                    save(translate(ob))
                    count += 1
                }
                pageCount += 1
            }
            offset += pageCount
            log.debug("Indexed {} prisoners", count)
        } while (pageCount > 0)
        return count
    }

}