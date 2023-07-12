package uk.gov.justice.digital.hmpps.prisonersearch

import org.elasticsearch.client.Request
import org.junit.jupiter.api.BeforeEach
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex

/**
 * Test class to initialise the standard set of search data only once.
 * Subclasses will get the same set of search data and we have implemented our own custom junit orderer to ensure that
 * no other type of tests run inbetween and cause a re-index to ruin our data.
 */
abstract class AbstractSearchDataIntegrationTest : QueueIntegrationTest() {

  private companion object {
    private var initialiseSearchData = true
  }

  fun resetSearchData() {
    initialiseSearchData = true
  }

  @BeforeEach
  fun setup() {
    if (initialiseSearchData) {
      createIndexStatusIndex(SyncIndex.INDEX_B)
      createPrisonerIndex(SyncIndex.INDEX_B)

      loadPrisonerData()

      initialiseSearchData = false
    }
  }

  private fun loadPrisonerData() {
    val prisoners = listOf(
      "A1090AA", "A7089EZ", "A7089FB", "A7089FX", "A7090AB", "A7090AD", "A7090AF", "A7090BB",
      "A7090BD", "A7090BF", "A9999AB", "A9999RA", "A9999RC", "A7089EY", "A7089FA", "A7089FC", "A7090AA", "A7090AC",
      "A7090AE", "A7090BA", "A7090BC", "A7090BE", "A9999AA", "A9999AC", "A9999RB",
    )
    prisoners.forEach {
      val prisonerRequest = Request("PUT", "/prisoner-search-b/_doc/$it").apply {
        setJsonEntity("/prisoners/prisoner$it.json".readResourceAsText())
      }
      elasticsearchClient.lowLevelClient.performRequest(prisonerRequest)
    }
  }
}
