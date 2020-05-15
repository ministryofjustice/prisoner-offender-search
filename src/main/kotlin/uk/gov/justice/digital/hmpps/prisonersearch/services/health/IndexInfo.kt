package uk.gov.justice.digital.hmpps.prisonersearch.services.health


import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.core.CountResponse
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexStatusService
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchClient


@Component
class IndexInfo(private var indexStatusService : IndexStatusService,
                private val searchClient: SearchClient) : InfoContributor {

  override fun contribute(builder : Info.Builder) {
    val indexStatus = indexStatusService.getCurrentIndex()
    builder.withDetail("index-status", indexStatus);
    builder.withDetail("index-size", mapOf(
      indexStatus.currentIndex.name to countIndex(indexStatus.currentIndex.indexName).count,
      indexStatus.currentIndex.otherIndex().name to countIndex(indexStatus.currentIndex.otherIndex().indexName).count
    ))
  }

  private fun countIndex(indexName: String): CountResponse {
    val searchSourceBuilder = SearchSourceBuilder().apply {
      query(QueryBuilders.matchAllQuery())
    }
    return searchClient.count(CountRequest(arrayOf(indexName), searchSourceBuilder))
  }

}
