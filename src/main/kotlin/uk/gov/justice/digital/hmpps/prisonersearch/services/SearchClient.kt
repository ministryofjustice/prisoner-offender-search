package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.apache.http.client.config.RequestConfig
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.core.CountResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service

private const val ONE_MINUTE = 60000

@Service
class SearchClient(
  private val elasticsearchClient: RestHighLevelClient,
  @param:Qualifier("elasticsearchOperations") private val elasticsearchOperations: ElasticsearchOperations
) {
  private val requestOptions =
    RequestOptions.DEFAULT.toBuilder().setRequestConfig(RequestConfig.custom().setSocketTimeout(ONE_MINUTE).build()).build()

  fun search(searchRequest: SearchRequest): SearchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
  fun count(countRequest: CountRequest): CountResponse = elasticsearchClient.count(countRequest, RequestOptions.DEFAULT)
  fun lowLevelClient(): RestClient = elasticsearchClient.lowLevelClient
  fun elasticsearchOperations(): ElasticsearchOperations = elasticsearchOperations
  fun scroll(searchScrollRequest: SearchScrollRequest): SearchResponse = elasticsearchClient.scroll(searchScrollRequest, requestOptions)
}
