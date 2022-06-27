package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.unit.TimeValue
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
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerInEstablishmentRequest
import java.util.concurrent.TimeUnit

@Service
class PrisonerInEstablishmentService(
  private val elasticsearchClient: SearchClient,
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

  fun search(prisonId: String, prisonerSearchRequest: PrisonerInEstablishmentRequest): Page<Prisoner> {
    log.info("Received keyword request ${gson.toJson(prisonerSearchRequest)}")

    val searchSourceBuilder = createSourceBuilder(prisonId, prisonerSearchRequest)
    val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)

    // Useful for logging the JSON elastic search query that is executed
    log.info("search query JSON: {}", searchSourceBuilder.toString())

    val searchResponse = elasticsearchClient.search(searchRequest)
    return createSearchResponse(prisonerSearchRequest.pagination, searchResponse).also {
      auditSearch(prisonerSearchRequest, searchResponse.hits.totalHits?.value ?: 0)
    }
  }

  private fun createSourceBuilder(
    prisonId: String,
    searchRequest: PrisonerInEstablishmentRequest
  ): SearchSourceBuilder {
    val pageable = PageRequest.of(searchRequest.pagination.page, searchRequest.pagination.size)
    return SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(pageable.pageSize.coerceAtMost(maxSearchResults))
      from(pageable.offset.toInt())
      trackTotalHits(true)
      query(buildKeywordQuery(prisonId, searchRequest))
      sort("lastName.keyword")
      sort("firstName.keyword")
      sort("prisonerNumber")
    }
  }

  private fun buildKeywordQuery(prisonId: String, searchRequest: PrisonerInEstablishmentRequest): BoolQueryBuilder {
    val keywordQuery = QueryBuilders.boolQuery()

    // Pattern match terms which might be NomsId, PNC or CRO & uppercase them
    val sanitisedKeywordRequest = PrisonerInEstablishmentRequest(
      term = addUppercaseKeywordTokens(searchRequest.term),
      pagination = searchRequest.pagination,
    )

    with(sanitisedKeywordRequest) {

      term.takeIf { !it.isNullOrBlank() }?.let {
        // Will include the prisoner document if any of the words specified match in any of the fields
        keywordQuery.must().add(
          generateMatchQuery(it)
        )
      }

      keywordQuery.filterWhenPresent("prisonId", prisonId)
    }

    return keywordQuery
  }

  private fun generateMatchQuery(
    term: String,
  ): QueryBuilder {
    val fields = listOf(
      "prisonerNumber",
      "pncNumber",
      "pncNumberCanonicalShort",
      "pncNumberCanonicalLong",
      "croNumber",
      "bookNumber",
      "firstName",
      "lastName",
    )
    return QueryBuilders.multiMatchQuery(term, *fields.toTypedArray())
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
      .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
      .operator(Operator.OR)
  }

  private fun createSearchResponse(
    paginationRequest: PaginationRequest,
    searchResponse: SearchResponse
  ): Page<Prisoner> {
    val pageable = PageRequest.of(paginationRequest.page, paginationRequest.size)
    val prisoners = getSearchResult(searchResponse)
    return if (prisoners.isEmpty()) {
      log.info("Establishment search: No prisoner matched this request. Returning empty response.")
      createEmptyResponse(paginationRequest)
    } else {
      log.info("Establishment search: Matches found. Page ${pageable.pageNumber} with ${prisoners.size} prisoners, totalHits ${searchResponse.hits.totalHits?.value}")
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

  private fun getIndex() = indexStatusService.getCurrentIndex().currentIndex.indexName

  private fun auditSearch(
    searchRequest: PrisonerInEstablishmentRequest,
    numberOfResults: Long,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "term" to searchRequest.term,
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble()
    )
    telemetryClient.trackEvent("POSFindInEstablisment", propertiesMap, metricsMap)
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
