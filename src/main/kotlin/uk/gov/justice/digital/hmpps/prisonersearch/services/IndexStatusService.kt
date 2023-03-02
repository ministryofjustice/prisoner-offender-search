package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.IndexStatusRepository
import java.time.LocalDateTime

@Service
class IndexStatusService(
  private val indexStatusRepository: IndexStatusRepository,
  private val telemetryClient: TelemetryClient,
  private val indexQueueService: IndexQueueService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getCurrentIndex(): IndexStatus {
    var indexStatus = indexStatusRepository.findByIdOrNull("STATUS")

    if (indexStatus == null) {
      indexStatus = IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false)
      indexStatusRepository.save(indexStatus)
    }
    return indexStatus
  }

  fun markRebuildStarting(): Boolean {
    val currentIndexStatus = getCurrentIndex()

    if (currentIndexStatus.inProgress.or(currentIndexStatus.inError)) {
      log.warn("Unable to mark rebuild for current index {}", currentIndexStatus)
      return false
    }
    currentIndexStatus.inProgress = true
    currentIndexStatus.startIndexTime = LocalDateTime.now()
    currentIndexStatus.endIndexTime = null
    indexStatusRepository.save(currentIndexStatus)

    telemetryClient.trackEvent(
      "POSIndexRebuildStarting",
      mapOf("indexName" to currentIndexStatus.currentIndex.indexName),
      null,
    )

    return true
  }

  fun cancelIndexing(): Unit = with(getCurrentIndex()) {
    if (!inProgress) return

    inProgress = false
    inError = false
    indexStatusRepository.save(this)
    log.warn("Indexing cancelled")

    telemetryClient.trackEvent("POSIndexRebuildCancelled", mapOf("indexName" to currentIndex.indexName), null)
  }

  fun switchIndex(): Boolean {
    val currentIndexStatus = getCurrentIndex()
    if (currentIndexStatus.inProgress.or(currentIndexStatus.inError)) {
      log.warn("Unable to switch index for current index {}", currentIndexStatus)
      return false
    }
    currentIndexStatus.toggleIndex()
    indexStatusRepository.save(currentIndexStatus)
    return true
  }

  fun markRebuildComplete(): Boolean =
    with(getCurrentIndex()) {
      val indexQueueStatus = indexQueueService.getIndexQueueStatus()
      if (inProgress && indexQueueStatus.active.not() && inError.not()) {
        inProgress = false
        endIndexTime = LocalDateTime.now()
        toggleIndex()
        indexStatusRepository.save(this)
        log.info("Index marked as complete, index {} is now current.", currentIndex)
        telemetryClient.trackEvent("POSIndexRebuildComplete", mapOf("indexName" to currentIndex.indexName), null)
        return true
      }

      log.info("Ignoring index build request with currentIndexStatus=$currentIndex and indexQueueStatus=$indexQueueStatus")
      return false
    }

  fun markIndexBuildFailure() {
    getCurrentIndex().inError = true
  }
}
