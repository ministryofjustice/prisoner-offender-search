package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.util.IOUtils
import com.google.gson.JsonParser
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.builder.Diff
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.search.ClearScrollRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.Request
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.search.Scroll
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.config.IndexProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerA
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerB
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerDifferenceService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictivePatient
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.ElasticSearchIndexingException
import kotlin.runCatching

@Service
class PrisonerIndexService(
  private val nomisService: NomisService,
  private val prisonerARepository: PrisonerARepository,
  private val prisonerBRepository: PrisonerBRepository,
  private val indexQueueService: IndexQueueService,
  private val indexStatusService: IndexStatusService,
  private val searchClient: SearchClient,
  private val telemetryClient: TelemetryClient,
  private val indexProperties: IndexProperties,
  private val restrictedPatientService: RestrictedPatientService,
  private val prisonerDifferenceService: PrisonerDifferenceService,
  private val incentivesService: IncentivesService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val cutoff = 50
  }

  fun indexPrisoner(prisonerId: String) {
    nomisService.getOffender(prisonerId)?.let {
      index(offenderBooking = it)
    } ?: run {
      telemetryClient.trackEvent(
        "POSOffenderNotFoundForIndexing",
        mapOf("prisonerID" to prisonerId),
        null,
      )
    }
  }

  fun comparePrisoner(prisonerId: String) {
    nomisService.getOffender(prisonerId)?.let {
      compare(offenderBooking = it)
    } ?: run {
      telemetryClient.trackEvent(
        "POSOffenderNotFoundForComparing",
        mapOf("prisonerID" to prisonerId),
        null,
      )
    }
  }

  fun syncPrisoner(prisonerId: String): Prisoner? =
    nomisService.getOffender(prisonerId)?.let {
      reindex(offenderBooking = it)
    }
      ?: get(prisonerId)?.run {
        delete(prisonerId)
        telemetryClient.trackEvent(
          "POSOffenderRemovedFromIndex",
          mapOf("prisonerID" to prisonerId),
          null,
        )
        log.info("POSOffenderRemovedFromIndex prisoner not in Nomis but was in index: {}", prisonerId)
        return this
      }
      ?: run {
        telemetryClient.trackEvent(
          "POSOffenderNotFoundForIndexing",
          mapOf("prisonerID" to prisonerId),
          null,
        )
        log.error("POSOffenderNotFoundForIndexing {}", prisonerId)
        return null
      }

  fun delete(prisonerNumber: String) {
    log.info("Delete Prisoner {}", prisonerNumber)
    telemetryClient.trackEvent("POSPrisonerDeleted", mapOf("nomsNumber" to prisonerNumber), null)

    prisonerARepository.deleteById(prisonerNumber)
    prisonerBRepository.deleteById(prisonerNumber)
  }

  fun get(id: String): Prisoner? {
    val currentIndexStatus = indexStatusService.getCurrentIndex()
    return if (currentIndexStatus.currentIndex == SyncIndex.INDEX_A) {
      prisonerARepository.findById(id)
    } else {
      prisonerBRepository.findById(id)
    }.map { it }.orElse(null)
  }

  // called when prisoner record has changed
  fun reindex(offenderBooking: OffenderBooking): Prisoner {
    val existingPrisoner = get(offenderBooking.offenderNo)

    val restrictedPatientData = offenderBooking.getRestrictedPatientData()
    val incentiveLevel = runCatching { offenderBooking.getIncentiveLevel() }

    val prisonerA = PrisonerA(existingPrisoner, offenderBooking, incentiveLevel, restrictedPatientData)
    val prisonerB = PrisonerB(existingPrisoner, offenderBooking, incentiveLevel, restrictedPatientData)

    val storedPrisoner = saveToRepository(indexStatusService.getCurrentIndex(), prisonerA, prisonerB)

    prisonerDifferenceService.handleDifferences(existingPrisoner, offenderBooking, storedPrisoner)

    // return message to DLQ since we have only done a partial update
    incentiveLevel.exceptionOrNull()?.run { throw this }

    log.trace("finished reindex() {}", offenderBooking)
    return storedPrisoner
  }

  // called when rebuilding the index from scratch
  fun index(offenderBooking: OffenderBooking): Prisoner {
    val restrictivePatient: RestrictivePatient? = offenderBooking.getRestrictedPatientData()
    val incentiveLevel = offenderBooking.getIncentiveLevel()

    val currentIndexStatus = indexStatusService.getCurrentIndex()

    val storedPrisoner = if (currentIndexStatus.currentIndex == SyncIndex.INDEX_A) {
      prisonerBRepository.save(PrisonerB(offenderBooking, incentiveLevel, restrictivePatient))
    } else {
      prisonerARepository.save(PrisonerA(offenderBooking, incentiveLevel, restrictivePatient))
    }

    log.trace("finished index() {}", offenderBooking)
    return storedPrisoner
  }

  fun compare(offenderBooking: OffenderBooking): List<Diff<Prisoner>> {
    val restrictivePatient: RestrictivePatient? = offenderBooking.getRestrictedPatientData()
    val incentiveLevel = offenderBooking.getIncentiveLevel()

    val calculated = PrisonerA(offenderBooking, incentiveLevel, restrictivePatient)
    val existing = get(offenderBooking.offenderNo)

    return prisonerDifferenceService.reportDifferences(existing, calculated)
  }

  private fun checkIfIndexExists(indexName: String): Boolean {
    val response = searchClient.lowLevelClient().performRequest(Request("HEAD", "/$indexName"))
    return response.statusLine.statusCode != 404
  }

  fun countIndex(prisonerIndex: SyncIndex): Int {
    val response = searchClient.lowLevelClient().performRequest(Request("get", "/${prisonerIndex.indexName}/_count"))
    return JsonParser.parseString(IOUtils.toString(response.entity.content)).asJsonObject["count"].asInt
  }

  @Throws(ElasticSearchIndexingException::class, IndexBuildException::class)
  fun buildIndex(): IndexStatus {
    try {
      if (indexStatusService.markRebuildStarting()) {
        val currentIndex = indexStatusService.getCurrentIndex().currentIndex
        val otherIndexCount = countIndex(currentIndex.otherIndex())
        log.info(
          "Current index is {} [{}], rebuilding index {} [{}]",
          currentIndex,
          countIndex(currentIndex),
          currentIndex.otherIndex(),
          otherIndexCount,
        )
        checkExistsAndReset(currentIndex.otherIndex())

        log.info("Sending rebuild request")
        indexQueueService.sendIndexRequestMessage(PrisonerIndexRequest(IndexRequestType.REBUILD))
      } else {
        log.info("Index not restarted as index is marked in progress or in error")
        throw IndexBuildException("Index is marked as in progress or in error")
      }
      return indexStatusService.getCurrentIndex()
    } catch (ese: ElasticsearchException) {
      indexStatusService.markIndexBuildFailure()
      throw ElasticSearchIndexingException(ese)
    } catch (uee: UncategorizedElasticsearchException) {
      indexStatusService.markIndexBuildFailure()
      throw ElasticSearchIndexingException(uee)
    }
  }

  fun startIndexReconciliation() {
    log.info("Sending compare request")
    indexQueueService.sendIndexRequestMessage(PrisonerIndexRequest(IndexRequestType.COMPARE))
  }

  internal fun checkExistsAndReset(prisonerIndex: SyncIndex) {
    if (checkIfIndexExists(prisonerIndex.indexName)) {
      searchClient.lowLevelClient().performRequest(Request("DELETE", "/${prisonerIndex.indexName}"))
    }
    await untilCallTo { checkIfIndexExists(prisonerIndex.indexName) } matches { it == false }
    createPrisonerIndex(prisonerIndex)
  }

  private fun createPrisonerIndex(prisonerIndex: SyncIndex) {
    val indexOperations = searchClient.elasticsearchOperations().indexOps(IndexCoordinates.of(prisonerIndex.indexName))
    indexOperations.create()
    indexOperations.putMapping(indexOperations.createMapping(if (prisonerIndex == SyncIndex.INDEX_A) PrisonerA::class.java else PrisonerB::class.java))
  }

  fun cancelIndex(): IndexStatus {
    indexStatusService.cancelIndexing()
    return indexStatusService.getCurrentIndex()
  }

  @Throws(SwitchIndexException::class)
  fun switchIndex(): IndexStatus {
    val switched = indexStatusService.switchIndex()
    if (!switched) {
      log.info("Index not switched as one is marked in progress or in error")
      throw SwitchIndexException("One is marked as in progress or in error")
    }
    val currentIndex = indexStatusService.getCurrentIndex()
    log.info("Index switched, index {} is now current.", currentIndex.currentIndex)
    return currentIndex
  }

  fun addOffendersToBeProcessed(pageRequest: PageRequest, operation: IndexRequestType) {
    var count = 0
    log.debug(
      "Sending offender indexing requests row {} --> {}",
      pageRequest.offset + 1,
      pageRequest.offset + pageRequest.pageSize,
    )
    nomisService.getOffendersIds(pageRequest.offset, pageRequest.pageSize).offenderIds?.forEach {
      indexQueueService.sendIndexRequestMessage(PrisonerIndexRequest(operation, it.offenderNumber))
      count += 1
    }
    log.debug("Requested {} offender index syncs", count)
  }

  @Throws(MarkIndexCompleteException::class)
  fun indexingComplete(ignoreThreshold: Boolean): IndexStatus {
    val currentIndexStatus = indexStatusService.getCurrentIndex()
    if (currentIndexStatus.inError) {
      log.info("Index not marked as complete as it is in error")
      throw MarkIndexCompleteException("Index is in error")
    }
    if (ignoreThreshold.not()) {
      val indexCount = countIndex(currentIndexStatus.currentIndex.otherIndex())
      if (indexCount <= indexProperties.completeThreshold) {
        log.info("Ignoring index build request, index ${currentIndexStatus.currentIndex.otherIndex()} has count $indexCount which is less than threshold ${indexProperties.completeThreshold}.")
        return currentIndexStatus
      }
    }

    if (indexStatusService.markRebuildComplete()) {
      indexQueueService.clearAllMessages()
    }
    return indexStatusService.getCurrentIndex()
  }

  fun addIndexRequestToQueue(operation: IndexRequestType): Long {
    log.debug("Sending list of offender requests")
    var page = 0
    val totalRows = nomisService.getOffendersIds(0, 1).totalRows
    if (totalRows > 0) {
      do {
        indexQueueService.sendIndexRequestMessage(
          PrisonerIndexRequest(
            operation,
            null,
            PageRequest.of(page, indexProperties.pageSize),
          ),
        )
        page += 1
      } while ((page) * indexProperties.pageSize < totalRows && indexStatusService.getCurrentIndex().inProgress && indexStatusService.getCurrentIndex().inError.not())
    }
    log.debug("Offender lists have been sent: {} requests for a total of {} offenders", page, totalRows)
    return totalRows
  }

  fun OffenderBooking.getRestrictedPatientData(): RestrictivePatient? =
    this.takeUnless { this.assignedLivingUnit?.agencyId != "OUT" }?.let {
      restrictedPatientService.getRestrictedPatient(this.offenderNo)?.let {
        RestrictivePatient(
          supportingPrisonId = it.supportingPrison.agencyId,
          dischargedHospital = it.hospitalLocation,
          dischargeDate = it.dischargeTime.toLocalDate(),
          dischargeDetails = it.commentText,
        )
      }
    }

  fun OffenderBooking.getIncentiveLevel(): IncentiveLevel? =
    this.bookingId?.let { bookingId -> incentivesService.getCurrentIncentive(bookingId) }

  fun getAllNomisOffenders(offset: Long, size: Int) = nomisService.getOffendersIds(offset, size)

  private fun saveToRepository(currentIndexStatus: IndexStatus, prisonerA: PrisonerA, prisonerB: PrisonerB): Prisoner =
    if (currentIndexStatus.currentIndex == SyncIndex.INDEX_A) {
      prisonerARepository.save(prisonerA)
    } else {
      prisonerBRepository.save(prisonerB)
    }.also {
      if (currentIndexStatus.inProgress) { // Keep changes in sync if rebuilding
        if (currentIndexStatus.currentIndex == SyncIndex.INDEX_A) {
          prisonerBRepository.save(prisonerB)
        } else {
          prisonerARepository.save(prisonerA)
        }
      }
    }

  @Async
  fun doCompare() {
    try {
      val start = System.currentTimeMillis()
      val (onlyInIndex, onlyInNomis) = compareIndex()
      val end = System.currentTimeMillis()
      telemetryClient.trackEvent(
        "POSIndexReport",
        mapOf(
          "onlyInIndex" to toLogMessage(onlyInIndex),
          "onlyInNomis" to toLogMessage(onlyInNomis),
          "timeMs" to (end - start).toString(),
        ),
        null,
      )
      log.info("End of doCompare()")
    } catch (e: Exception) {
      log.error("compare failed", e)
    }
  }

  private fun toLogMessage(onlyList: List<String>): String =
    if (onlyList.size <= cutoff) onlyList.toString() else onlyList.slice(IntRange(0, cutoff)).toString() + "..."

  fun compareIndex(): Pair<List<String>, List<String>> {
    val allNomis =
      getAllNomisOffenders(0, Int.MAX_VALUE)
        .offenderIds!!
        .map { it.offenderNumber }
        .sorted()

    val scroll = Scroll(TimeValue.timeValueMinutes(1L))
    val searchResponse = setupIndexSearch(scroll)

    var scrollId = searchResponse.scrollId
    var searchHits = searchResponse.hits.hits

    val allIndex = mutableListOf<String>()

    while (!searchHits.isNullOrEmpty()) {
      allIndex.addAll(searchHits.map { it.id })

      val scrollRequest = SearchScrollRequest(scrollId)
      scrollRequest.scroll(scroll)
      val scrollResponse = searchClient.scroll(scrollRequest)
      scrollId = scrollResponse.scrollId
      searchHits = scrollResponse.hits.hits
    }
    log.info("compareIndex(): allIndex=${allIndex.size}, allNomis=${allNomis.size}")

    val clearScrollRequest = ClearScrollRequest()
    clearScrollRequest.addScrollId(scrollId)
    val clearScrollResponse = searchClient.clearScroll(clearScrollRequest)
    log.info("clearScroll isSucceeded=${clearScrollResponse.isSucceeded}, numFreed=${clearScrollResponse.numFreed}")

    allIndex.sort()

    val onlyInIndex = allIndex - allNomis.toSet()
    val onlyInNomis = allNomis - allIndex.toSet()

    return Pair(onlyInIndex, onlyInNomis)
  }

  private fun setupIndexSearch(scroll: Scroll): SearchResponse {
    val searchSourceBuilder = SearchSourceBuilder().apply {
      fetchSource(FetchSourceContext.DO_NOT_FETCH_SOURCE)
      size(2000)
    }
    val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
    searchRequest.scroll(scroll)
    return searchClient.search(searchRequest)
  }

  private fun getIndex() = indexStatusService.getCurrentIndex().currentIndex.indexName

  open class IndexBuildException(val error: String) : Exception("Unable to build index reason: $error")

  open class SwitchIndexException(val error: String) : Exception("Unable to switch indexes: $error")

  open class MarkIndexCompleteException(val error: String) : Exception("Unable mark index as complete: $error")
}
