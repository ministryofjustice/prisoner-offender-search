package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.util.IOUtils
import com.google.gson.JsonParser
import org.elasticsearch.client.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
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
                           val searchClient: SearchClient,
                           @Value("\${index.page.size:1000}") val pageSize : Int
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun indexPrisoner(prisonerId: String) : Prisoner? {
        return nomisService.getOffender(prisonerId)?.let {
            buildIndex(it)
        }
    }

    fun delete(prisonerNumber : String) {
        log.info("Delete Prisoner {}", prisonerNumber)

        prisonerARepository.deleteById(prisonerNumber)
        prisonerBRepository.deleteById(prisonerNumber)
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

    fun countIndex(indexName: String): Int {
        val response = searchClient.lowLevelClient().performRequest(Request("get", "/$indexName/_count"))
        return JsonParser.parseString(IOUtils.toString(response.entity.content)).asJsonObject["count"].asInt
    }

    fun buildIndex() : IndexStatus {
        if (indexStatusService.markRebuildStarting()) {
            val currentIndex = indexStatusService.getCurrentIndex().currentIndex
            val otherIndexCount = countIndex(currentIndex.otherIndex().indexName)
            log.info("Current index is {} [{}], rebuilding index {} [{}]", currentIndex,
                countIndex(currentIndex.indexName),
                currentIndex.otherIndex(),
                otherIndexCount
            )

            searchClient.lowLevelClient()
                .performRequest(Request("DELETE", "/${currentIndex.otherIndex().indexName}"))

            searchClient.elasticsearchOperations().index(IndexQueryBuilder()
                .withObject(if (currentIndex.otherIndex() == SyncIndex.INDEX_A) PrisonerA() else PrisonerB())
                .build())

            searchClient.elasticsearchOperations().putMapping(if (currentIndex.otherIndex() == SyncIndex.INDEX_A) PrisonerA::class.java else PrisonerB::class.java)

            log.info("Sending rebuild request")
            indexQueueService.sendIndexRequestMessage(PrisonerIndexRequest(IndexRequestType.REBUILD))
        }
        return indexStatusService.getCurrentIndex()
    }

    fun cancelIndex() : IndexStatus {
        indexStatusService.cancelIndexing()
        return indexStatusService.getCurrentIndex()
    }

    fun addOffendersToBeIndexed(pageRequest : PageRequest) {
        var count = 0
        log.debug("Sending offender indexing requests row {} --> {}", pageRequest.offset+1, pageRequest.offset + pageRequest.pageSize)
        nomisService.getOffendersIds(pageRequest.offset, pageRequest.pageSize).offenderIds?.forEach {
            indexQueueService.sendIndexRequestMessage(PrisonerIndexRequest(IndexRequestType.OFFENDER, it.offenderNumber))
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
                    PrisonerIndexRequest(
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

    private fun buildIndex(offenderBooking: OffenderBooking) : Prisoner? {
        val currentIndexStatus = indexStatusService.getCurrentIndex()

        if (currentIndexStatus.inProgress) {
            if (currentIndexStatus.currentIndex == SyncIndex.INDEX_A) {
                return prisonerBRepository.save(translate(PrisonerB(), offenderBooking))
            } else {
                return prisonerARepository.save(translate(PrisonerA(), offenderBooking))
            }
        }
        return null
    }
}