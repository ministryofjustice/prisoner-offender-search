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
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.security.AuthenticationHolder
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.BadRequestException

@Service
class PrisonerSearchService(
  private val searchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: AuthenticationHolder
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val RESULT_HITS_MAX = 1000
  }

  fun findBySearchCriteria(searchCriteria: SearchCriteria): List<Prisoner> {
    validateSearchForm(searchCriteria)
    if (searchCriteria.prisonerIdentifier != null) {
      queryBy(searchCriteria) { idMatch(it) } onMatch {
        customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
        return it.matches
      }
    }
    if (!(searchCriteria.firstName.isNullOrBlank() && searchCriteria.lastName.isNullOrBlank())) {
      if (searchCriteria.includeAliases) {
        queryBy(searchCriteria) { nameMatchWithAliases(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return it.matches
        }
      } else {
        queryBy(searchCriteria) { nameMatch(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return it.matches
        }
      }
    }
    customEventForFindBySearchCriteria(searchCriteria, 0)
    return emptyList()
  }

  private fun validateSearchForm(searchCriteria: SearchCriteria) {
    if (!searchCriteria.isValid()) {
      log.warn("Invalid search  - no criteria provided")
      throw BadRequestException("Invalid search  - please provide at least 1 search parameter")
    }
  }

  private fun queryBy(
    searchCriteria: SearchCriteria,
    queryBuilder: (searchCriteria: SearchCriteria) -> BoolQueryBuilder?
  ): Result {
    val query = queryBuilder(searchCriteria)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query.withDefaults(searchCriteria))
        size(RESULT_HITS_MAX)
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val prisonerMatches = getSearchResult(searchClient.search(searchRequest))
      return if (prisonerMatches.isEmpty()) Result.NoMatch else Result.Match(prisonerMatches)
    } ?: Result.NoMatch
  }

  private fun queryBy(
    searchCriteria: PrisonerListCriteria,
    queryBuilder: (searchCriteria: PrisonerListCriteria) -> BoolQueryBuilder?
  ): Result {
    val query = queryBuilder(searchCriteria)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        size(RESULT_HITS_MAX)
        query(it)
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val prisonerMatches = getSearchResult(searchClient.search(searchRequest))
      return if (prisonerMatches.isEmpty()) Result.NoMatch else Result.Match(prisonerMatches)
    } ?: Result.NoMatch
  }

  private fun matchByIds(searchCriteria: PrisonerListCriteria): BoolQueryBuilder? {
    return shouldMatchOneOf("prisonerNumber", searchCriteria.prisonerNumbers)
  }

  private fun idMatch(searchCriteria: SearchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .mustMultiMatchKeyword(
          prisonerIdentifier?.canonicalPNCNumber(),
          "prisonerNumber",
          "bookingId",
          "pncNumber",
          "croNumber",
          "bookNumber"
        )
    }
  }

  private fun nameMatch(searchCriteria: SearchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
            )
        )
    }
  }

  private fun nameMatchWithAliases(searchCriteria: SearchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
            )
            .should(
              QueryBuilders.nestedQuery(
                "aliases",
                QueryBuilders.boolQuery()
                  .should(
                    QueryBuilders.boolQuery()
                      .mustWhenPresent("aliases.lastName", lastName)
                      .mustWhenPresent("aliases.firstName", firstName)
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

  private fun getIndex(): String {
    return indexStatusService.getCurrentIndex().currentIndex.indexName
  }

  fun findByListOfPrisonerNumbers(prisonerListCriteria: PrisonerListCriteria): List<Prisoner> {
    if (!prisonerListCriteria.isValid()) {
      log.warn("Invalid search  - no prisoner numbers provided")
      throw BadRequestException("Invalid search  - please provide a minimum of 1 and a maximum of 200 prisoner numbers")
    }

    queryBy(prisonerListCriteria) { matchByIds(it) } onMatch {
      customEventForFindByListOfPrisonerNumbers(prisonerListCriteria.prisonerNumbers.size, it.matches.size)
      return it.matches
    }
    return emptyList()
  }

  private fun customEventForFindBySearchCriteria(searchCriteria: SearchCriteria, numberOfResults: Int) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "lastname" to searchCriteria.lastName,
      "firstname" to searchCriteria.firstName,
      "prisonId" to searchCriteria.prisonId,
      "prisonerIdentifier" to searchCriteria.prisonerIdentifier,
      "includeAliases" to searchCriteria.includeAliases.toString()
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble()
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }

  private fun customEventForFindByListOfPrisonerNumbers(prisonerListNumber: Int, numberOfResults: Int) {
    val logMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "numberOfPrisonerIds" to prisonerListNumber.toString()
    )

    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble()
    )
    telemetryClient.trackEvent("POSFindByListOfPrisonerNumbers", logMap, metricsMap)
  }
}

sealed class Result {
  object NoMatch : Result()
  data class Match(val matches: List<Prisoner>) : Result()
}

inline infix fun Result.onMatch(block: (Result.Match) -> Nothing): Unit? {
  return when (this) {
    is Result.NoMatch -> {
    }
    is Result.Match -> block(this)
  }
}

private fun BoolQueryBuilder.withDefaults(searchCriteria: SearchCriteria): BoolQueryBuilder? {
  return this
    .filterWhenPresent("prisonId", searchCriteria.prisonId)
}
