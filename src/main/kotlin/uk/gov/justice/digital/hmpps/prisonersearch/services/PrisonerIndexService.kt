package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.translateA
import uk.gov.justice.digital.hmpps.prisonersearch.model.translateB
import uk.gov.justice.digital.hmpps.prisonersearch.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import java.time.LocalDateTime.now

@Service
class PrisonerIndexService(val nomisService: NomisService,
                           val prisonerARepository: PrisonerARepository,
                           val prisonerBRepository: PrisonerBRepository,
                           val indexQueueService : IndexQueueService,
                           val indexStatusRepository : IndexStatusRepository
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun save(offenderBooking: OffenderBooking) : Prisoner {
        val currentIndexStatus = indexStatusRepository.findById("STATUS")
        if (currentIndexStatus.isEmpty) {
            val prisonerIndexed = prisonerARepository.save(translateA(offenderBooking))
            val indexStatus = IndexStatus("STATUS", "index-1", now(), now(), false)
            indexStatusRepository.save(indexStatus)
            return prisonerIndexed
        } else {
            if (currentIndexStatus.get().currentIndex == "index-1") {
                return prisonerARepository.save(translateA(offenderBooking))
            } else {
                return prisonerBRepository.save(translateB(offenderBooking))
            }
        }

    }

    fun indexActivePrisonersInPrison(prisonId : String) : Int {
        // check index status
        val currentIndexStatus = indexStatusRepository.findById("PRISONINDEX-$prisonId")
        if (currentIndexStatus.isEmpty) {

            val indexStatus = IndexStatus("PRISONINDEX-$prisonId", "index-prison", now(), null, true)
            return indexPrison(indexStatus, prisonId)

        } else {
            val indexStatus = currentIndexStatus.get()
            if (indexStatus.inProgress) {
                log.warn("Index already in progress, skipping...")
            } else {
                // rebuild
                return indexPrison(indexStatus, prisonId)
            }
        }
        return 0
    }

    fun indexAll() : Int {
        // check index status
        val currentIndexStatus = indexStatusRepository.findById("STATUS")
        if (currentIndexStatus.isEmpty) {
            val indexStatus = IndexStatus("STATUS", "index-1", now(), null, true)
            return rebuild(indexStatus)

        } else {
            val indexStatus = currentIndexStatus.get()
            if (indexStatus.inProgress) {
                log.warn("Index already in progress, skipping...")
            } else {
                // rebuild & switch
                return rebuild(IndexStatus("STATUS", if (indexStatus.currentIndex == "index-1") "index-2" else "index-1", now(), null, true))
            }
        }
        return 0
    }

    private fun indexPrison(indexStatus: IndexStatus, prisonId : String) : Int {
        indexStatusRepository.save(indexStatus)

        log.debug("Sending Index Requests for active Prisoners in {}", prisonId)
        var count = 0
        var offset = 0
        do {
            var numberReturned = 0
            nomisService.getOffendersByPrison(prisonId, offset, pageSizeToRetrieve())?.forEach {
                indexQueueService.sendIndexRequestMessage(IndexRequest(IndexRequestType.OFFENDER, it.offenderNo))
                count += 1
                numberReturned += 1
            }
            offset += pageSizeToRetrieve()
            log.debug("Requested {} so far, number returned {}", count, numberReturned)
        } while (numberReturned > pageSize())

        indexStatus.inProgress = false
        indexStatus.endIndexTime = now()
        indexStatusRepository.save(indexStatus)

        return count
    }



    private fun rebuild(indexStatus: IndexStatus): Int {
        var count = 0
        indexStatusRepository.save(indexStatus)

        log.debug("Sending Indexing Requests")

        var offset = 0
        do {
            var numberReturned = 0
            nomisService.getOffendersIds(offset, pageSizeToRetrieve())?.forEach {
                indexQueueService.sendIndexRequestMessage(IndexRequest(IndexRequestType.OFFENDER, it.offenderNumber))
                count += 1
                numberReturned += 1
            }
            offset += pageSizeToRetrieve()
            log.debug("Requested {} so far, number returned {}", count, numberReturned)
        } while (numberReturned > pageSize())

        indexStatus.inProgress = false
        indexStatus.endIndexTime = now()
        indexStatusRepository.save(indexStatus)
        return count
    }

    private fun pageSize() = 100
    private fun pageSizeToRetrieve() = pageSize()+1

}