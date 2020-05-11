package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
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
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.BadRequestException

@Service
class PrisonerSearchService(
  private val searchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val gson : Gson
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun findBySearchCriteria(searchCriteria: SearchCriteria): List<Prisoner> {
    validateSearchForm(searchCriteria)
    if (searchCriteria.prisonerIdentifier != null) {
      queryBy(searchCriteria) { idMatch(it) } onMatch { return it.matches }
    }
    if (!(searchCriteria.firstName.isNullOrBlank() && searchCriteria.lastName.isNullOrBlank())) {
      if (searchCriteria.includeAliases) {
        queryBy(searchCriteria) { nameMatchWithAliases(it) } onMatch { return it.matches }
      } else {
        queryBy(searchCriteria) { nameMatch(it) } onMatch { return it.matches }
      }
    }
    return emptyList()
  }

  private fun validateSearchForm(searchCriteria: SearchCriteria) {
    if (!searchCriteria.isValid) {
      log.warn("Invalid search  - no criteria provided")
      throw BadRequestException("Invalid search  - please provide at least 1 search parameter")
    }
  }

  private fun queryBy(searchCriteria: SearchCriteria, queryBuilder: (searchCriteria: SearchCriteria) -> BoolQueryBuilder?): Result {
    val query = queryBuilder(searchCriteria)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query.withDefaults(searchCriteria))
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val prisonerMatches = getSearchResult(searchClient.search(searchRequest))
      return if (prisonerMatches.isEmpty()) Result.NoMatch else Result.Match(prisonerMatches)
    } ?: Result.NoMatch
  }

  private fun idMatch(searchCriteria: SearchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .mustMultiMatchKeyword(prisonerIdentifier?.canonicalPNCNumber(), "prisonerNumber", "bookingId", "pncNumber", "bookNumber")
    }
  }

  private fun nameMatch(searchCriteria: SearchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(QueryBuilders.boolQuery()
          .should(QueryBuilders.boolQuery()
            .mustWhenPresent("lastName", lastName)
            .mustWhenPresent("firstName", firstName)
          )
        )
    }
  }

  private fun nameMatchWithAliases(searchCriteria: SearchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(QueryBuilders.boolQuery()
          .should(QueryBuilders.boolQuery()
            .mustWhenPresent("lastName", lastName)
            .mustWhenPresent("firstName", firstName)
          )
          .should(QueryBuilders.nestedQuery("aliases",
            QueryBuilders.boolQuery()
              .should(QueryBuilders.boolQuery()
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

  private fun getIndex(): String{
    return if (indexStatusService.getCurrentIndex().currentIndex == SyncIndex.INDEX_A) {
      "prisoner-search-a"
    } else {
      "prisoner-search-b"
    }
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