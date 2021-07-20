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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.security.AuthenticationHolder

@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@Service
class RestrictedPatientSearchService(
  private val searchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: AuthenticationHolder
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun findBySearchCriteria(searchCriteria: RestrictedPatientSearchCriteria, pageable: Pageable): Page<Prisoner> {
    if (searchCriteria.isEmpty()) {
      queryBy(searchCriteria, pageable) { anyMatch() } onMatch {
        customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
        return PageImpl(it.matches, pageable, it.totalHits)
      }
    } else {
      if (searchCriteria.prisonerIdentifier != null) {
        queryBy(searchCriteria, pageable) { idMatch(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return PageImpl(it.matches, pageable, it.totalHits)
        }
      }
      if (!(searchCriteria.firstName.isNullOrBlank() && searchCriteria.lastName.isNullOrBlank())) {
        queryBy(searchCriteria, pageable) { nameMatchWithAliases(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return PageImpl(it.matches, pageable, it.totalHits)
        }
      }
    }
    customEventForFindBySearchCriteria(searchCriteria, 0)
    return PageImpl(listOf(), pageable, 0L)
  }

  private fun queryBy(
    searchCriteria: RestrictedPatientSearchCriteria,
    pageable: Pageable,
    queryBuilder: (searchCriteria: RestrictedPatientSearchCriteria) -> BoolQueryBuilder?
  ): RestrictedPatientResult {
    val query = queryBuilder(searchCriteria)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query.withDefaults())
        size(pageable.pageSize)
        from(pageable.offset.toInt())
        sort("prisonerNumber")
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val searchResults = searchClient.search(searchRequest)
      val prisonerMatches = getSearchResult(searchResults)
      return if (prisonerMatches.isEmpty()) RestrictedPatientResult.NoMatch else RestrictedPatientResult.Match(
        prisonerMatches,
        searchResults.hits.totalHits?.value ?: 0
      )
    } ?: RestrictedPatientResult.NoMatch
  }

  private fun idMatch(searchCriteria: RestrictedPatientSearchCriteria): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .mustMultiMatchKeyword(
          prisonerIdentifier?.prisonerNumberOrCanonicalPNCNumber(),
          "prisonerNumber",
          "bookingId",
          "pncNumber",
          "pncNumberCanonicalShort",
          "pncNumberCanonicalLong",
          "croNumber",
          "bookNumber"
        )
    }
  }

  private fun nameMatchWithAliases(searchCriteria: RestrictedPatientSearchCriteria): BoolQueryBuilder? {
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
                  ),
                ScoreMode.Max
              )
            )
        )
    }
  }

  private fun anyMatch(): BoolQueryBuilder {
    return QueryBuilders.boolQuery()
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("search found ${searchHits.size} hits")
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun getIndex(): String {
    return indexStatusService.getCurrentIndex().currentIndex.indexName
  }

  private fun customEventForFindBySearchCriteria(searchCriteria: RestrictedPatientSearchCriteria, numberOfResults: Int) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "lastname" to searchCriteria.lastName,
      "firstname" to searchCriteria.firstName,
      "prisonerIdentifier" to searchCriteria.prisonerIdentifier,
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble()
    )
    telemetryClient.trackEvent("POSFindRestrictedPatientsByCriteria", propertiesMap, metricsMap)
  }
}

sealed class RestrictedPatientResult {
  object NoMatch : RestrictedPatientResult()
  data class Match(val matches: List<Prisoner>, val totalHits: Long) : RestrictedPatientResult()
}

inline infix fun RestrictedPatientResult.onMatch(block: (RestrictedPatientResult.Match) -> Nothing) =
  when (this) {
    is RestrictedPatientResult.NoMatch -> {
    }
    is RestrictedPatientResult.Match -> block(this)
  }

private fun BoolQueryBuilder.withDefaults(): BoolQueryBuilder {
  return this.must("restrictedPatient", true)
}
