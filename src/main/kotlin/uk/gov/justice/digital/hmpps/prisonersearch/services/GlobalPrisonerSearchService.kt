package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.security.AuthenticationHolder
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.BadRequestException

@Service
class GlobalPrisonerSearchService(
  private val searchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: AuthenticationHolder
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun findByGlobalSearchCriteria(globalSearchCriteria: GlobalSearchCriteria, pageable: Pageable): Page<Prisoner> {
    validateSearchForm(globalSearchCriteria)
    if (globalSearchCriteria.offenderNo != null) {
      queryBy(globalSearchCriteria,pageable) { idMatch(it) } onMatch {
        customEventForFindBySearchCriteria(globalSearchCriteria, it.matches.size)
        return PageImpl(it.matches, pageable, it.totalHits)
      }
    }
    if (!(globalSearchCriteria.firstName.isNullOrBlank() && globalSearchCriteria.lastName.isNullOrBlank())) {
      if (globalSearchCriteria.includeAliases) {
        queryBy(globalSearchCriteria,pageable) { nameMatchWithAliases(it) } onMatch {
          customEventForFindBySearchCriteria(globalSearchCriteria, it.matches.size)
          val ret = PageImpl(it.matches, pageable, it.totalHits)
          return ret
        }
      } else {
        queryBy(globalSearchCriteria,pageable) { nameMatch(it) } onMatch {
          customEventForFindBySearchCriteria(globalSearchCriteria, it.matches.size)
          return PageImpl(it.matches, pageable, it.totalHits)
        }
      }
    }
    return return PageImpl(listOf(), pageable, 0L)
  }

  private fun validateSearchForm(globalSearchCriteria: GlobalSearchCriteria) {
    if (!globalSearchCriteria.isValid) {
      log.warn("Invalid search  - no criteria provided")
      throw BadRequestException("Invalid search  - please provide at least 1 search parameter")
    }
  }

  private fun queryBy(
    globalSearchCriteria: GlobalSearchCriteria,
    pageable: Pageable,
    queryBuilder: (globalSearchCriteria: GlobalSearchCriteria) -> BoolQueryBuilder?
  ): GlobalResult {
    val query = queryBuilder(globalSearchCriteria)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query.withDefaults(globalSearchCriteria))
        size(pageable.pageSize)
        from(pageable.pageNumber)
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val searchResults = searchClient.search(searchRequest)
      val prisonerMatches = getSearchResult(searchResults)
      return if (prisonerMatches.isEmpty()) GlobalResult.NoMatch else GlobalResult.Match(prisonerMatches,searchResults.hits.totalHits?.value ?: 0)
    } ?: GlobalResult.NoMatch
  }

  private fun idMatch(globalSearchCriteria: GlobalSearchCriteria): BoolQueryBuilder? {
    with(globalSearchCriteria) {
      return QueryBuilders.boolQuery()
        .mustMultiMatchKeyword(
          offenderNo?.canonicalPNCNumber(),
          "prisonerNumber",
          "bookingId",
          "pncNumber",
          "croNumber",
          "bookNumber"
        )
    }
  }

  private fun nameMatch(globalSearchCriteria: GlobalSearchCriteria): BoolQueryBuilder? {
    with(globalSearchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
                .mustWhenPresent("gender", gender?.value)
                .mustWhenPresent("dateOfBirth", dateOfBirth)
            )
        )
    }
  }

  private fun nameMatchWithAliases(globalSearchCriteria: GlobalSearchCriteria): BoolQueryBuilder? {
    with(globalSearchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
                .mustWhenPresent("gender", gender?.value)
                .mustWhenPresent("dateOfBirth", dateOfBirth)
            )
            .should(
              QueryBuilders.nestedQuery(
                "aliases",
                QueryBuilders.boolQuery()
                  .should(
                    QueryBuilders.boolQuery()
                      .mustWhenPresent("aliases.lastName", lastName)
                      .mustWhenPresent("aliases.firstName", firstName)
                      .mustWhenPresent("gender", gender?.value)
                      .mustWhenPresent("dateOfBirth", dateOfBirth)
                  ), ScoreMode.Max
              )
            )
        )
    }
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("search found ${searchHits.size} hits")
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun getIndex() = indexStatusService.getCurrentIndex().currentIndex.indexName


  private fun customEventForFindBySearchCriteria(
    globalSearchCriteria: GlobalSearchCriteria,
    numberOfResults: Int
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "lastname" to globalSearchCriteria.lastName,
      "firstname" to globalSearchCriteria.firstName,
      "gender" to globalSearchCriteria.gender?.value,
      "prisonId" to globalSearchCriteria.location,
      "dateOfBirth" to globalSearchCriteria.dateOfBirth.toString(),
      "prisonerIdentifier" to globalSearchCriteria.offenderNo,
      "includeAliases" to globalSearchCriteria.includeAliases.toString()
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble()
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }
}

sealed class GlobalResult {
  object NoMatch : GlobalResult()
  data class Match(val matches: List<Prisoner>, val totalHits: Long) : GlobalResult()
}

inline infix fun GlobalResult.onMatch(block: (GlobalResult.Match) -> Nothing): Unit? {
  return when (this) {
    is GlobalResult.NoMatch -> {
    }
    is GlobalResult.Match -> block(this)
  }
}

private fun BoolQueryBuilder.withDefaults(globalSearchCriteria: GlobalSearchCriteria) =
  when (globalSearchCriteria.location) {
    "IN" -> mustNotWhenPresent("prisonId", "OUT")
    "OUT" -> filterWhenPresent("prisonId", "OUT")
  else -> this
  }

