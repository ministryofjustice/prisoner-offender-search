package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex.INDEX_A
import uk.gov.justice.digital.hmpps.prisonersearch.repository.IndexStatusRepository
import java.time.LocalDateTime
import java.util.Optional

class IndexStatusServiceTest {

  private val indexStatusRepository = mock<IndexStatusRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val indexQueueService = mock<IndexQueueService>()
  private val indexStatusService = IndexStatusService(indexStatusRepository, telemetryClient, indexQueueService)

  @Nested
  inner class MarkRebuildComplete {

    @Test
    fun `fails if indexing not in progress`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = false, inError = false)))

      assertThat(indexStatusService.markRebuildComplete()).isFalse
    }

    @Test
    fun `complete build fails if index status in error`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = true)))
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))

      assertThat(indexStatusService.markRebuildComplete()).isFalse
    }

    @Test
    fun `fails if indexing not in progress but in error`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = false, inError = true)))

      assertThat(indexStatusService.markRebuildComplete()).isFalse
    }

    @Test
    fun `fails if index queue has active messages`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = false)))
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(1, 0, 0))

      assertThat(indexStatusService.markRebuildComplete()).isFalse
    }

    @Test
    fun `completes build if OK`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = false)))
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))

      assertThat(indexStatusService.markRebuildComplete()).isTrue
    }
  }

  @Nested
  inner class MarkRebuildStarting {
    @Test
    fun `start index rebuild fails if indexing already in progress and there is an error`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = true)))
      assertThat(indexStatusService.markRebuildStarting()).isFalse
    }
    @Test
    fun `start index rebuild fails if there is an error`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = false, inError = true)))
      assertThat(indexStatusService.markRebuildStarting()).isFalse
    }
    @Test
    fun `start index rebuild fails if indexing already in progress`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = false)))
      assertThat(indexStatusService.markRebuildStarting()).isFalse
    }
    @Test
    fun `start index rebuild successfully completes`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = false, inError = false)))
      assertThat(indexStatusService.markRebuildStarting()).isTrue
    }
  }

  @Nested
  inner class SwitchIndex {

    @Test
    fun `switch index fails if indexing already in progress and there is an error`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = true)))
      assertThat(indexStatusService.switchIndex()).isFalse
    }
    @Test
    fun `switch index fails if there is an error`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = false, inError = true)))
      assertThat(indexStatusService.switchIndex()).isFalse
    }
    @Test
    fun `switch index fails if indexing already in progress`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = false)))
      assertThat(indexStatusService.switchIndex()).isFalse
    }
    @Test
    fun `switch index successfully completes`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = false, inError = false)))
      assertThat(indexStatusService.switchIndex()).isTrue
    }
  }

  @Nested
  inner class CancelIndex {
    @Test
    fun `cancelling index resets index error state and inProgress flag to false`() {
      whenever(indexStatusRepository.findById("STATUS")).thenReturn(Optional.of(anIndexStatus(inProgress = true, inError = true)))
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))

      assertThat(indexStatusService.getCurrentIndex().inProgress).isTrue
      assertThat(indexStatusService.getCurrentIndex().inError).isTrue
      assertThat(indexStatusService.cancelIndexing()).isTrue
      assertThat(indexStatusService.getCurrentIndex().inProgress).isFalse
      assertThat(indexStatusService.getCurrentIndex().inError).isFalse
    }
  }
}

fun anIndexStatus(inProgress: Boolean = true, inError: Boolean = false) =
  IndexStatus(
    id = "STATUS",
    currentIndex = INDEX_A,
    startIndexTime = LocalDateTime.now().minusHours(1L),
    endIndexTime = LocalDateTime.now().minusMinutes(1L),
    inProgress = inProgress,
    inError = inError
  )
