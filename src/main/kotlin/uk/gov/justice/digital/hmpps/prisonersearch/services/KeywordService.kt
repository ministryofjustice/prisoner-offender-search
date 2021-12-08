package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.core.TimeValue
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MultiMatchQueryBuilder
import org.elasticsearch.index.query.Operator
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
  @Value("\${search.keyword.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.keyword.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private fun validateKeywordRequest(keywordRequest: KeywordRequest) {
    if (keywordRequest.prisonIds.isNullOrEmpty()) {
      log.warn("Invalid keyword search  - no prisonIds specified to filter by")
      throw BadRequestException("Invalid keyword search  - please provide prison locations to filter by")
    }
  }

  fun findByKeyword(keywordRequest: KeywordRequest): Page<Prisoner> {
    log.info("Received keyword request ${gson.toJson(keywordRequest)}")

    validateKeywordRequest(keywordRequest)
    val searchSourceBuilder = createSourceBuilder(keywordRequest)
    val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)

    // Useful for logging the JSON elastic search query that is executed
    // log.info("Keyword query JSON: {}", searchSourceBuilder.toString())

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

    // Pattern match terms which might be NomsId, PNC or CRO & uppercase them
    val sanitisedKeywordRequest = KeywordRequest(
      orWords = addUppercaseKeywordTokens(keywordRequest.orWords),
      andWords = addUppercaseKeywordTokens(keywordRequest.andWords),
      exactPhrase = addUppercaseKeywordTokens(keywordRequest.exactPhrase),
      notWords = addUppercaseKeywordTokens(keywordRequest.notWords),
      prisonIds = keywordRequest.prisonIds,
      fuzzyMatch = keywordRequest.fuzzyMatch ?: false,
      pagination = keywordRequest.pagination,
    )

    with(sanitisedKeywordRequest) {

      andWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will include the prisoner document if all of the words specified match in any of the fields
        keywordQuery.must().add(
          generateMatchQuery(it, fuzzyMatch!!, Operator.AND, MultiMatchQueryBuilder.Type.CROSS_FIELDS)
        )
      }

      orWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will include the prisoner document if any of the words specified match in any of the fields
        keywordQuery.must().add(
          generateMatchQuery(it, fuzzyMatch!!, Operator.OR, MultiMatchQueryBuilder.Type.BEST_FIELDS)
        )
      }

      notWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will exclude the prisoners with any of these words matching anywhere in document
        keywordQuery.mustNot(
          QueryBuilders.multiMatchQuery(it, "*", "aliases.*", "alerts.*")
            .lenient(true)
            .fuzzyTranspositions(false)
            .operator(Operator.OR)
        )
      }

      exactPhrase.takeIf { !it.isNullOrBlank() }?.let {
        // Will include prisoner where this exact phrase appears anywhere in the document
        keywordQuery.must().add(
          generateMatchQuery(it, fuzzyMatch!!, Operator.AND, MultiMatchQueryBuilder.Type.PHRASE)
        )
      }

      prisonIds.takeIf { it != null && it.isNotEmpty() && it[0].isNotBlank() }?.let {
        // Filter to return only those documents that contain the prison locations specified by the client
        keywordQuery.filterWhenPresent("prisonId", it)
      }
    }

    return keywordQuery
  }

  private fun generateMatchQuery(
    term: String,
    fuzzyMatch: Boolean,
    operator: Operator,
    multiMatchType: MultiMatchQueryBuilder.Type,
  ): QueryBuilder {
    return QueryBuilders.multiMatchQuery(term, "*", "aliases.*", "alerts.*")
      // Boost the scores for specific fields so real names and IDs are ranked higher than alias matches
      .analyzer("whitespace")
      .field("lastName", 10f)
      .field("firstName", 10f)
      .field("prisonerNumber", 10f)
      .field("pncNumber", 10f)
      .field("pncNumberCanonicalShort", 10f)
      .field("pncNumberCanonicalLong", 10f)
      .field("croNumber", 10f)
      .lenient(true)
      .type(multiMatchType)
      .fuzzyTranspositions(fuzzyMatch)
      .operator(operator)
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
      // Useful when checking the content of test results
      // log.info("Response content = ${gson.toJson(response)}")
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

  /*
  ** Some fields are defined as @Keyword in the ES mapping annotations so will not match when the query
  ** tokens are provided in lower or mixed case. Detect these and replace with an uppercase variant.
  */

  private fun addUppercaseKeywordTokens(tokens: String?): String? {
    if (tokens.isNullOrEmpty()) {
      return tokens
    }
    var newTokens = ""
    val arrayOfTokens = tokens.split("\\s+".toRegex())
    arrayOfTokens.forEach {
      newTokens += if (it.isPrisonerNumber() || it.isCroNumber() || it.isPncNumber()) {
        "${it.uppercase()} "
      } else {
        "${it.lowercase()} "
      }
    }
    return newTokens.trim()
  }

  private fun String.isPncNumber() =
    matches("^\\d{4}/([0-9]+)[a-zA-Z]$".toRegex()) || matches("^\\d{2}/([0-9]+)[a-zA-Z]$".toRegex())

  private fun String.isCroNumber() =
    matches("^([0-9]+)/([0-9]+)[a-zA-Z]$".toRegex())
}
