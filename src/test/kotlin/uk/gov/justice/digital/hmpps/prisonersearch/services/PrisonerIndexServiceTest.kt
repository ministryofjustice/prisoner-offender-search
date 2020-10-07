package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository

class PrisonerIndexServiceTest {

    private val nomisService = mock<NomisService>()
    private val prisonerARepository = mock<PrisonerARepository>()
    private val prisonerBRepository = mock<PrisonerBRepository>()
    private val indexQueueService = mock<IndexQueueService>()
    private val indexStatusService = mock<IndexStatusService>()
    private val searchClient = mock<SearchClient>()
    private val telemetryClient = mock<TelemetryClient>()
    private val prisonerIndexService = PrisonerIndexService(nomisService, prisonerARepository, prisonerBRepository, indexQueueService, indexStatusService, searchClient, telemetryClient, 1000)

    @Test
    fun `indexingComplete - clear messages if index build is complete`() {
        whenever(indexStatusService.markRebuildComplete()).thenReturn(true)

        prisonerIndexService.indexingComplete()

        verify(indexQueueService).clearAllMessages()
    }

    @Test
    fun `indexingComplete - do not clear messages if index build is not complete`() {
        whenever(indexStatusService.markRebuildComplete()).thenReturn(false)

        prisonerIndexService.indexingComplete()

        verifyZeroInteractions(indexQueueService)
    }

}