package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.*
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking

@Service
class PrisonerIndexService(val nomisService: NomisService,
                           val prisonerARepository: PrisonerARepository,
                           val prisonerBRepository: PrisonerBRepository,
                           val indexQueueService : IndexQueueService,
                           val indexStatusService: IndexStatusService,
                           @Value("\${index.page.size:1000}") val pageSize : Int
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun indexPrisoner(prisonerId: String) {
        nomisService.getOffender(prisonerId)?.let {
            buildIndex(it)
        }
    }

    fun sync(offenderBooking: OffenderBooking)  {
        val currentIndexStatus = indexStatusService.getCurrentIndex()

        if (currentIndexStatus.currentIndex == SyncIndex.INDEX_A) {
            prisonerARepository.save(translate(PrisonerA(), offenderBooking))
        } else {
            prisonerBRepository.save(translate(PrisonerB(), offenderBooking))
        }
        buildIndex(offenderBooking)  // Keep changes in sync if rebuilding
    }

    fun buildIndex() : IndexStatus {
        if (indexStatusService.markRebuildStarting()) {
            val currentIndex = indexStatusService.getCurrentIndex().currentIndex
            log.info("Current Index is {}, rebuilding index {}", currentIndex, currentIndex.otherIndex())
            if (currentIndex == SyncIndex.INDEX_A) {
                prisonerBRepository.deleteAll()
            } else {
                prisonerARepository.deleteAll()
            }
            log.info("Sending rebuild request")
            indexQueueService.sendIndexRequestMessage(IndexRequest(IndexRequestType.REBUILD))
        }
        return indexStatusService.getCurrentIndex()
    }

    fun indexingComplete() : IndexStatus {
        indexStatusService.markRebuildComplete()
        indexQueueService.clearAllMessages()
        val currentIndex = indexStatusService.getCurrentIndex()
        log.info("Index marked as Complete, Index {} is now current.", currentIndex.currentIndex)
        return currentIndex
    }

    fun addIndexRequestToQueue(): Int {
        var count = 0

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
        } while (numberReturned > pageSize && indexStatusService.getCurrentIndex().inProgress)
        log.debug("All Rebuild messages sent {}", count)
        return count
    }

    private fun buildIndex(offenderBooking: OffenderBooking) {
        val currentIndexStatus = indexStatusService.getCurrentIndex()

        if (currentIndexStatus.inProgress) {
            if (currentIndexStatus.currentIndex == SyncIndex.INDEX_A) {
                prisonerBRepository.save(translate(PrisonerB(), offenderBooking))
            } else {
                prisonerARepository.save(translate(PrisonerA(), offenderBooking))
            }
        }
    }

    private fun pageSizeToRetrieve() = pageSize+1

}