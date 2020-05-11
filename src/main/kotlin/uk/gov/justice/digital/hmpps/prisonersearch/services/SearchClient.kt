package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class SearchClient(
    @param:Qualifier("elasticSearchClient") private val elasticSearchClient: RestHighLevelClient
) {
  fun search(searchRequest: SearchRequest): SearchResponse = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT)
}