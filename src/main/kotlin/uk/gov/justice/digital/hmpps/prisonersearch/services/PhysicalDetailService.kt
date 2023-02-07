package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.QueryBuilders.boolQuery
import org.elasticsearch.index.query.QueryBuilders.rangeQuery
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
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PhysicalDetailRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.BadRequestException
import java.util.concurrent.TimeUnit

@Service
class PhysicalDetailService(
  private val elasticsearchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: AuthenticationHolder,
  @Value("\${search.detailed.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.detailed.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private fun validateDetailRequest(detailRequest: PhysicalDetailRequest): Unit = with(detailRequest) {
    if (prisonIds.isNullOrEmpty()) {
      throw BadRequestException("Please provide prison locations to filter by")
    }
    if (!cellLocationPrefix.isNullOrEmpty() && prisonIds.size > 1) {
      throw BadRequestException("Cell location prefix can only be used for when searching in one prison")
    }
    if ((minHeight ?: 0) < 0) throw BadRequestException("Minimum height cannot be less than 0")
    if ((maxHeight ?: 0) < 0) throw BadRequestException("Maximum height cannot be less than 0")
    if ((minHeight ?: 0) > (maxHeight ?: Int.MAX_VALUE)) {
      throw BadRequestException("Maximum height cannot be less than the minimum height")
    }
    if ((minWeight ?: 0) < 0) throw BadRequestException("Minimum weight cannot be less than 0")
    if ((maxWeight ?: 0) < 0) throw BadRequestException("Maximum weight cannot be less than 0")
    if ((minWeight ?: 0) > (maxWeight ?: Int.MAX_VALUE)) {
      throw BadRequestException("Maximum weight cannot be less than the minimum weight")
    }
  }

  fun findByPhysicalDetail(detailRequest: PhysicalDetailRequest): Page<Prisoner> {
    log.info("Received physical detail search request {}", gson.toJson(detailRequest))

    validateDetailRequest(detailRequest)
    val searchSourceBuilder = createSourceBuilder(detailRequest)
    val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)

    // Useful for logging the JSON elastic search query that is executed
    // log.info("Detail search query JSON: {}", searchSourceBuilder.toString())

    return try {
      val searchResponse = elasticsearchClient.search(searchRequest)
      customEventForFindBySearchCriteria(detailRequest, searchResponse.hits.totalHits?.value ?: 0)
      createDetailResponse(detailRequest.pagination, searchResponse)
    } catch (e: Throwable) {
      log.error("Elastic search exception", e)
      val pageable = PageRequest.of(detailRequest.pagination.page, detailRequest.pagination.size)
      return PageImpl(emptyList(), pageable, 0L)
    }
  }

  private fun createSourceBuilder(detailRequest: PhysicalDetailRequest): SearchSourceBuilder {
    val pageable = PageRequest.of(detailRequest.pagination.page, detailRequest.pagination.size)
    return SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(pageable.pageSize.coerceAtMost(maxSearchResults))
      from(pageable.offset.toInt())
      sort("_score")
      sort("prisonerNumber")
      trackTotalHits(true)
      query(buildDetailQuery(detailRequest))
    }
  }

  private fun buildDetailQuery(detailRequest: PhysicalDetailRequest): BoolQueryBuilder {
    val detailQuery = boolQuery().also {
      it.should(QueryBuilders.matchAllQuery())
    }

    with(detailRequest) {
      // Filter by prison establishments provided
      prisonIds.takeIf { !it.isNullOrEmpty() && it[0].isNotBlank() }?.let {
        detailQuery.filterWhenPresent("prisonId", it)
      }

      // and restrict to single cell location prefix
      val singlePrisonId = prisonIds?.singleOrNull()
      // if specified single prison then restrict to cell location
      cellLocationPrefix.takeIf { singlePrisonId != null }?.removePrefix("$singlePrisonId-")
        ?.let { detailQuery.filter(QueryBuilders.prefixQuery("cellLocation.keyword", it)) }

      gender?.let {
        detailQuery.filter(
          boolQuery()
            .should(QueryBuilders.matchQuery("gender", it))
            .should(QueryBuilders.matchQuery("aliases.gender", it))
        )
      }
      ethnicity?.let {
        detailQuery.filter(
          boolQuery()
            .should(QueryBuilders.matchPhraseQuery("ethnicity", it))
            .should(QueryBuilders.matchPhraseQuery("aliases.ethnicity", it))
        )
      }

      minHeight?.let { detailQuery.filter(rangeQuery("heightCentimetres").gte(it)) }
      maxHeight?.let { detailQuery.filter(rangeQuery("heightCentimetres").lte(it)) }

      minWeight?.let { detailQuery.filter(rangeQuery("weightKilograms").gte(it)) }
      maxWeight?.let { detailQuery.filter(rangeQuery("weightKilograms").lte(it)) }

      hairColour?.let { detailQuery.filter(QueryBuilders.matchPhraseQuery("hairColour", it)) }
      rightEyeColour?.let { detailQuery.filter(QueryBuilders.matchPhraseQuery("rightEyeColour", it)) }
      leftEyeColour?.let { detailQuery.filter(QueryBuilders.matchPhraseQuery("leftEyeColour", it)) }
      facialHair?.let { detailQuery.filter(QueryBuilders.matchPhraseQuery("facialHair", it)) }
      shapeOfFace?.let { detailQuery.filter(QueryBuilders.matchPhraseQuery("shapeOfFace", it)) }
      build?.let { detailQuery.filter(QueryBuilders.matchPhraseQuery("build", it)) }

      minShoeSize?.let { detailQuery.filter(rangeQuery("shoeSize").gte(it)) }
      maxShoeSize?.let { detailQuery.filter(rangeQuery("shoeSize").lte(it)) }
    }

    return detailQuery
  }

  private fun createDetailResponse(
    paginationRequest: PaginationRequest,
    searchResponse: SearchResponse,
  ): Page<Prisoner> {
    val pageable = PageRequest.of(paginationRequest.page, paginationRequest.size)
    val prisoners = getSearchResult(searchResponse)
    return if (prisoners.isEmpty()) {
      log.info("Physical detail search: No prisoner matched this request. Returning empty response.")
      PageImpl(emptyList(), pageable, 0L)
    } else {
      log.info("Physical detail search: Matches found. Page ${pageable.pageNumber} with ${prisoners.size} prisoners, totalHits ${searchResponse.hits.totalHits?.value}")
      val response = PageImpl(prisoners, pageable, searchResponse.hits.totalHits!!.value)
      // Useful when checking the content of test results
      // log.info("Response content = ${gson.toJson(response)}")
      response
    }
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("Physical detail search: found {} hits", searchHits.size)
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun getIndex() = indexStatusService.getCurrentIndex().currentIndex.indexName

  private fun customEventForFindBySearchCriteria(
    detailRequest: PhysicalDetailRequest,
    numberOfResults: Long,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "cellLocationPrefix" to detailRequest.cellLocationPrefix,
      "minHeight" to detailRequest.minHeight?.toString(),
      "maxHeight" to detailRequest.maxHeight?.toString(),
      "minWeight" to detailRequest.minWeight?.toString(),
      "maxWeight" to detailRequest.maxWeight?.toString(),
      "prisonIds" to detailRequest.prisonIds.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByPhysicalDetails", propertiesMap, metricsMap)
  }
}
