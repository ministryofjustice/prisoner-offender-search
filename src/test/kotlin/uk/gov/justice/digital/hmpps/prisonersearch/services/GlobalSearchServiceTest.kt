package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.lucene.search.TotalHits
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.action.search.ClearScrollResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchResponse.Clusters
import org.elasticsearch.action.search.SearchResponseSections
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.security.AuthenticationHolder

private const val dummyDocId = 100

class GlobalSearchServiceTest {

  private val prisonerIndexService = mock<PrisonerIndexService>()
  private val indexStatusService = mock<IndexStatusService>()
  private val searchClient = mock<SearchClient>()
  private val telemetryClient = mock<TelemetryClient>()
  private val authenticationHolder = mock<AuthenticationHolder>()

  private val globalSearchService = GlobalSearchService(
    searchClient,
    indexStatusService,
    prisonerIndexService,
    Gson(),
    telemetryClient,
    authenticationHolder,
  )

  private fun resultsOf(offenders: List<String>): SearchResponse {
    val searchHits = offenders.map { SearchHit(dummyDocId, it, null, null, null) }
    val hits =
      SearchHits(searchHits.toTypedArray(), TotalHits(offenders.size.toLong(), TotalHits.Relation.EQUAL_TO), 10f)
    val searchResponseSections = SearchResponseSections(hits, null, null, false, null, null, 5)
    return SearchResponse(searchResponseSections, "myScroll", 8, 8, 0, 8, arrayOf(), Clusters.EMPTY)
  }

  @Nested
  inner class CompareIndex {

    private fun setupIndex(vararg prisonerNumbers: String) {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(
        IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false)
      )
      whenever(searchClient.search(any())).thenReturn(resultsOf(prisonerNumbers.asList()))
      whenever(searchClient.scroll(any())).thenReturn(resultsOf(emptyList()))
      whenever(searchClient.clearScroll(any())).thenReturn(ClearScrollResponse(true, 1))
    }

    private fun setupNomis(vararg prisonerNumbers: String) {
      whenever(prisonerIndexService.getAllNomisOffenders(0, Int.MAX_VALUE)).thenReturn(
        OffenderResponse(
          prisonerNumbers.map { OffenderId(it) }
        )
      )
    }

    @Test
    fun `Both same`() {
      setupIndex("A1234AA", "A1234AB")
      setupNomis("A1234AB", "A1234AA") // this is sorted

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).isEmpty()
      assertThat(onlyInNomis).isEmpty()
    }

    @Test
    fun `One different in each at start`() {
      setupIndex("A1234AB", "A1234AC")
      setupNomis("A1234AA", "A1234AC")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A1234AB")
      assertThat(onlyInNomis).containsExactly("A1234AA")
    }

    @Test
    fun `One different in each at end`() {
      setupIndex("A1234AA", "A1234AB")
      setupNomis("A1234AA", "A1234AC")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A1234AB")
      assertThat(onlyInNomis).containsExactly("A1234AC")
    }

    @Test
    fun `One different in each in middle`() {
      setupIndex("A1234AA", "A1234AB", "A1234AZ")
      setupNomis("A1234AA", "A1234AC", "A1234AZ")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A1234AB")
      assertThat(onlyInNomis).containsExactly("A1234AC")
    }

    @Test
    fun `Two different in each in middle`() {
      setupIndex("A1234AA", "A1234AB", "A1234AD", "A1234AZ")
      setupNomis("A1234AA", "A1234AC", "A1234AE", "A1234AZ")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A1234AB", "A1234AD")
      assertThat(onlyInNomis).containsExactly("A1234AC", "A1234AE")
    }

    @Test
    fun `One extra nomis in middle`() {
      setupIndex("A1234AA", "A1234AZ")
      setupNomis("A1234AA", "A1234AC", "A1234AZ")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).isEmpty()
      assertThat(onlyInNomis).containsExactly("A1234AC")
    }

    @Test
    fun `One extra index in middle`() {
      setupIndex("A1234AA", "A1234AC", "A1234AZ")
      setupNomis("A1234AA", "A1234AZ")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A1234AC")
      assertThat(onlyInNomis).isEmpty()
    }

    @Test
    fun `Two extra nomis in middle`() {
      setupIndex("A1234AA", "A1234AZ")
      setupNomis("A1234AA", "A1234AC", "A1234AD", "A1234AZ")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).isEmpty()
      assertThat(onlyInNomis).containsExactly("A1234AC", "A1234AD")
    }

    @Test
    fun `Two extra index in middle`() {
      setupIndex("A1234AA", "A1234AC", "A1234AD", "A1234AZ")
      setupNomis("A1234AA", "A1234AZ")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A1234AC", "A1234AD")
      assertThat(onlyInNomis).isEmpty()
    }

    @Test
    fun `Extra Nomis at start`() {
      setupIndex("A1234AA", "A1234AB")
      setupNomis("A0001AA", "A0002AA", "A1234AA", "A1234AB")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).isEmpty()
      assertThat(onlyInNomis).containsExactly("A0001AA", "A0002AA")
    }

    @Test
    fun `Extra Index at start`() {
      setupIndex("A0001AA", "A0002AA", "A1234AA", "A1234AB")
      setupNomis("A1234AA", "A1234AB")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A0001AA", "A0002AA")
      assertThat(onlyInNomis).isEmpty()
    }

    @Test
    fun `Two extra Index at end`() {
      setupIndex("A1234AA", "A1234AB", "A9999AA", "A9999AB")
      setupNomis("A1234AA", "A1234AB")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A9999AA", "A9999AB")
      assertThat(onlyInNomis).isEmpty()
    }

    @Test
    fun `Two extra Nomis at end`() {
      setupIndex("A1234AA", "A1234AB")
      setupNomis("A1234AA", "A1234AB", "A9999AA", "A9999AB")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).isEmpty()
      assertThat(onlyInNomis).containsExactly("A9999AA", "A9999AB")
    }

    @Test
    fun `Index empty`() {
      setupIndex()
      setupNomis("A1234AA", "A1234AB")

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).isEmpty()
      assertThat(onlyInNomis).containsExactly("A1234AA", "A1234AB")
    }

    @Test
    fun `Nomis empty`() {
      setupIndex("A1234AA", "A1234AB")
      setupNomis()

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).containsExactly("A1234AA", "A1234AB")
      assertThat(onlyInNomis).isEmpty()
    }

    @Test
    fun `Both empty`() {
      setupIndex()
      setupNomis()

      val (onlyInIndex, onlyInNomis) = globalSearchService.compareIndex()

      assertThat(onlyInIndex).isEmpty()
      assertThat(onlyInNomis).isEmpty()
    }
  }
}
