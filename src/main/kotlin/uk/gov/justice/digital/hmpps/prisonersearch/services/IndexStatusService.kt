package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.IndexStatusRepository
import java.time.LocalDateTime
import java.util.Optional

@Service
class IndexStatusService(
  private val indexStatusRepository: IndexStatusRepository,
  private val telemetryClient: TelemetryClient,
  private val indexQueueService: IndexQueueService
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getCurrentIndex(): IndexStatus {
    var indexStatus = indexStatusRepository.findById("STATUS").toNullable()

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
      null
    )

    return true
  }

  fun cancelIndexing(): Boolean {
    val currentIndexStatus = getCurrentIndex()
    if (currentIndexStatus.inProgress) {
      currentIndexStatus.inProgress = false
      currentIndexStatus.inError = false
      indexStatusRepository.save(currentIndexStatus)
      log.warn("Indexing cancelled")
      return true
    }

    return false
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

  fun markRebuildComplete(): Boolean {
    val currentIndexStatus = getCurrentIndex()
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    if (currentIndexStatus.inProgress && indexQueueStatus.active.not() && currentIndexStatus.inError.not()) {
      currentIndexStatus.inProgress = false
      currentIndexStatus.endIndexTime = LocalDateTime.now()
      currentIndexStatus.toggleIndex()
      indexStatusRepository.save(currentIndexStatus)
      log.info("Index marked as complete, index {} is now current.", currentIndexStatus.currentIndex)
      return true
    }

    log.info("Ignoring index build request with currentIndexStatus=$currentIndexStatus and indexQueueStatus=$indexQueueStatus")
    return false
  }

  fun markIndexBuildFailure() {
    getCurrentIndex().inError = true
  }
}

fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)
