package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.core.CountResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service

@Service
class SearchClient(
  private val elasticsearchClient: RestHighLevelClient,
  @param:Qualifier("elasticsearchOperations") private val elasticsearchOperations: ElasticsearchOperations
) {
  fun search(searchRequest: SearchRequest): SearchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
  fun count(countRequest: CountRequest): CountResponse = elasticsearchClient.count(countRequest, RequestOptions.DEFAULT)
  fun lowLevelClient(): RestClient = elasticsearchClient.lowLevelClient
  fun elasticsearchOperations(): ElasticsearchOperations = elasticsearchOperations
}
