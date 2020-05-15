package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.IndexStatusRepository
import java.time.LocalDateTime
import java.util.*

@Service
class IndexStatusService( val indexStatusRepository : IndexStatusRepository) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getCurrentIndex() : IndexStatus {
    val indexStatus = indexStatusRepository.findById("STATUS").toNullable()

    if (indexStatus == null) {
      indexStatus = IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false)
      indexStatusRepository.save(indexStatus)
    }
    return indexStatus
  }

  fun markRebuildStarting() : Boolean {
    val currentIndexStatus = getCurrentIndex()

    if (currentIndexStatus.inProgress) {
      log.warn("Index marked as already in progress")
      return false
    }
    currentIndexStatus.inProgress = true
    currentIndexStatus.startIndexTime = LocalDateTime.now()
    currentIndexStatus.endIndexTime = null
    indexStatusRepository.save(currentIndexStatus)

    return true
  }

  fun markRebuildComplete() : Boolean {
    val currentIndexStatus = getCurrentIndex()
    if (currentIndexStatus.inProgress) {
      currentIndexStatus.inProgress = false
      currentIndexStatus.endIndexTime = LocalDateTime.now()
      currentIndexStatus.toggleIndex()
      indexStatusRepository.save(currentIndexStatus)
      return true
    }

    log.warn("Index not marked as already in progress")
    return false;
  }
}

fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null);