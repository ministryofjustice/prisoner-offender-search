package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
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
            log.info("Current index is {}, rebuilding index {}", currentIndex, currentIndex.otherIndex())
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

    fun addOffendersToBeIndexed(pageRequest : PageRequest) {
        var count = 0
        log.debug("Sending offender indexing requests row {} --> {}", pageRequest.offset+1, pageRequest.offset + pageRequest.pageSize)
        nomisService.getOffendersIds(pageRequest.offset, pageRequest.pageSize).offenderIds?.forEach {
            indexQueueService.sendIndexRequestMessage(IndexRequest(IndexRequestType.OFFENDER, it.offenderNumber))
            count += 1
        }
        log.debug("Requested {} offender index syncs", count)
   }

    fun indexingComplete() : IndexStatus {
        indexStatusService.markRebuildComplete()
        indexQueueService.clearAllMessages()
        val currentIndex = indexStatusService.getCurrentIndex()
        log.info("Index marked as complete, index {} is now current.", currentIndex.currentIndex)
        return currentIndex
    }

    fun addIndexRequestToQueue(): Long {
        log.debug("Sending list of offender requests")
        var page = 0
        val totalRows = nomisService.getOffendersIds(0, 1).totalRows
        if (totalRows > 0) {
            do {
                indexQueueService.sendIndexRequestMessage(
                    IndexRequest(
                        IndexRequestType.OFFENDER_LIST,
                        null,
                        PageRequest.of(page, pageSize)
                    )
                )
                page += 1
            } while ((page) * pageSize < totalRows && indexStatusService.getCurrentIndex().inProgress)
        }
        log.debug("Offender lists have been sent: {} requests for a total of {} offenders", page, totalRows)
        return totalRows
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
}