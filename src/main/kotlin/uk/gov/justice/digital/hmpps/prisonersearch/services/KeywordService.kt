package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MultiMatchQueryBuilder
import org.elasticsearch.index.query.Operator
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.security.AuthenticationHolder
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.BadRequestException
import java.util.concurrent.TimeUnit

@Service
class KeywordService(
  private val elasticSearchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: AuthenticationHolder,
) {
  // Inject properties via constructor @Value
  private val maxSearchResults = 200
  private val searchTimeoutSeconds = 10L

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private fun validateKeywordRequest(keywordRequest: KeywordRequest) {
    if (keywordRequest.prisonIds?.isEmpty() != false) {
      log.warn("Invalid keyword search  - no prisonIds specified to filter by")
      throw BadRequestException("Invalid keyword search  - please provide prison locations to filter by")
    }
  }

  fun findByKeyword(keywordRequest: KeywordRequest): Page<Prisoner> {
    log.info("Received keyword request ${gson.toJson(keywordRequest)}")

    validateKeywordRequest(keywordRequest)
    val searchSourceBuilder = createSourceBuilder(keywordRequest)
    val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)

    // TODO: Useful for now but comment this
    log.info("Keyword query JSON: {}", searchSourceBuilder.toString())

    return try {
      val searchResponse = elasticSearchClient.search(searchRequest)
      customEventForFindBySearchCriteria(keywordRequest, searchResponse.hits.totalHits?.value ?: 0)
      createKeywordResponse(keywordRequest.pagination, searchResponse)
    } catch (e: Throwable) {
      log.error("Elastic search exception: $e")
      createEmptyResponse(keywordRequest.pagination)
    }
  }

  private fun createSourceBuilder(keywordRequest: KeywordRequest): SearchSourceBuilder {
    val pageable = PageRequest.of(keywordRequest.pagination.page, keywordRequest.pagination.size)
    return SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(pageable.pageSize.coerceAtMost(maxSearchResults))
      from(pageable.offset.toInt())
      sort("_score")
      sort("prisonerNumber")
      trackTotalHits(true)
      query(buildKeywordQuery(keywordRequest))
    }
  }

  private fun buildKeywordQuery(keywordRequest: KeywordRequest): BoolQueryBuilder {
    val keywordQuery = QueryBuilders.boolQuery()

    if (noKeyWordsSpecified(keywordRequest)) {
      keywordQuery.should(QueryBuilders.matchAllQuery())
    }

    with(keywordRequest) {

      andWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will match only if all of these words are present across all text fields - can be spread over multiple.
        keywordQuery.must(
          QueryBuilders.multiMatchQuery(it)
            .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
            .lenient(true)
            .fuzzyTranspositions(keywordRequest.fuzzyMatch!!)
            .operator(Operator.AND)
        )
      }

      orWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will match ANY of these words in any text fields
        keywordQuery
          .must(
            QueryBuilders.multiMatchQuery(it)
              .lenient(true)
              .fuzzyTranspositions(keywordRequest.fuzzyMatch!!)
              .operator(Operator.OR)
          )
      }

      notWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will exclude these words from matching if occur in any text fields
        keywordQuery.mustNot(
          QueryBuilders.multiMatchQuery(it)
            .lenient(true)
            .fuzzyTranspositions(keywordRequest.fuzzyMatch!!)
            .operator(Operator.OR)
        )
      }

      exactPhrase.takeIf { !it.isNullOrBlank() }?.let {
        // Will match only the exact phrase in any text field
        keywordQuery.must(
          QueryBuilders.multiMatchQuery(it)
            .lenient(true)
            .fuzzyTranspositions(keywordRequest.fuzzyMatch!!)
            .type(MultiMatchQueryBuilder.Type.PHRASE)
        )
      }

      prisonIds.let {
        // Filter to only the prison location codes specified by the client
        keywordQuery.filter(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .filter(QueryBuilders.matchQuery("prisonId", it!!.joinToString(separator = " ")))
            )
            //.should(
            // QueryBuilders.boolQuery()
            //   .mustNot(QueryBuilders.existsQuery("prisonId"))
            //)
        )
      }
    }

    return keywordQuery
  }

  private fun createKeywordResponse(paginationRequest: PaginationRequest, searchResponse: SearchResponse): Page<Prisoner> {
    val pageable = PageRequest.of(paginationRequest.page, paginationRequest.size)
    val prisoners = getSearchResult(searchResponse)
    return if (prisoners.isEmpty()) {
      log.info("Keyword search: No prisoner matched this request. Returning empty response.")
      createEmptyResponse(paginationRequest)
    } else {
      log.info("Keyword search: Matches found. Page ${pageable.pageNumber} with ${prisoners.size} prisoners, totalHits ${searchResponse.hits.totalHits?.value}")
      val response = PageImpl(prisoners, pageable, searchResponse.hits.totalHits!!.value)
      log.info("Response content = ${gson.toJson(response)}")
      response
    }
  }

  private fun createEmptyResponse(paginationRequest: PaginationRequest): Page<Prisoner> {
    val pageable = PageRequest.of(paginationRequest.page, paginationRequest.size)
    return PageImpl(emptyList(), pageable, 0L)
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("Keyword search: found ${searchHits.size} hits")
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun noKeyWordsSpecified(request: KeywordRequest): Boolean {
    return request.andWords.isNullOrEmpty() &&
      request.exactPhrase.isNullOrEmpty() &&
      request.orWords.isNullOrEmpty() &&
      request.notWords.isNullOrEmpty()
  }

  private fun getIndex() = indexStatusService.getCurrentIndex().currentIndex.indexName

  private fun customEventForFindBySearchCriteria(
    keywordRequest: KeywordRequest,
    numberOfResults: Long,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "andWords" to keywordRequest.andWords,
      "orWords" to keywordRequest.orWords,
      "notWords" to keywordRequest.notWords,
      "exactPhrase" to keywordRequest.exactPhrase,
      "prisonIds" to keywordRequest.prisonIds.toString(),
      "fuzzyMatch" to keywordRequest.fuzzyMatch.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble()
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }
}
