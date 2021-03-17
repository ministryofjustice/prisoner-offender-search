package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.apache.http.StatusLine
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.IndexOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import uk.gov.justice.digital.hmpps.prisonersearch.config.IndexProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
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
  private val indexProperties = mock<IndexProperties>()
  private val prisonerIndexService = PrisonerIndexService(
    nomisService,
    prisonerARepository,
    prisonerBRepository,
    indexQueueService,
    indexStatusService,
    searchClient,
    telemetryClient,
    indexProperties
  )

  @Nested
  inner class IndexingComplete {

    @Test
    fun `clear messages if index build is complete`() {
      whenever(indexStatusService.markRebuildComplete()).thenReturn(true)

      prisonerIndexService.indexingComplete(true)

      verify(indexQueueService).clearAllMessages()
    }

    @Test
    fun `do not clear messages if index build is not complete`() {
      whenever(indexStatusService.markRebuildComplete()).thenReturn(false)

      prisonerIndexService.indexingComplete(true)

      verifyZeroInteractions(indexQueueService)
    }
  }

  @Nested
  inner class CheckExistsAndReset {

    private val restClient = mock<RestClient>()
    private val elasticsearchOperations = mock<ElasticsearchOperations>()
    private val indexOperations = mock<IndexOperations>()

    @BeforeEach
    fun `mock ES clients`() {
      whenever(searchClient.lowLevelClient()).thenReturn(restClient)
      whenever(searchClient.elasticsearchOperations()).thenReturn(elasticsearchOperations)
      whenever(elasticsearchOperations.indexOps(any<IndexCoordinates>())).thenReturn(indexOperations)
    }

    @Test
    fun `waits for index to be gone before trying to recreate it`() {
      val indexExists = mockIndexExists()
      val indexMissing = mockIndexMissing()

      whenever(restClient.performRequest(Request("HEAD", "/prisoner-search-a")))
        .thenReturn(indexExists)
        .thenReturn(indexExists)
        .thenReturn(indexMissing)

      prisonerIndexService.checkExistsAndReset(SyncIndex.INDEX_A)

      verify(restClient).performRequest(Request("DELETE", "/prisoner-search-a"))
      verify(restClient, times(3)).performRequest(Request("HEAD", "/prisoner-search-a"))
      verify(indexOperations).create()
    }

    private fun mockIndexMissing(): Response {
      val checkExistsBadResponse = mock<Response>()
      val checkExistsBadStatusLine = mock<StatusLine>()
      whenever(checkExistsBadResponse.statusLine).thenReturn(checkExistsBadStatusLine)
      whenever(checkExistsBadStatusLine.statusCode).thenReturn(404)
      return checkExistsBadResponse
    }

    private fun mockIndexExists(): Response {
      val checkExistsGoodResponse = mock<Response>()
      val checkExistsGoodStatusLine = mock<StatusLine>()
      whenever(checkExistsGoodResponse.statusLine).thenReturn(checkExistsGoodStatusLine)
      whenever(checkExistsGoodStatusLine.statusCode).thenReturn(200)
      return checkExistsGoodResponse
    }
  }
}
