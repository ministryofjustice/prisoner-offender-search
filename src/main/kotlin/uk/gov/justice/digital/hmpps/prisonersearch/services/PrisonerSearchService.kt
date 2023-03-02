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
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.BookingIds
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.PrisonerNumbers
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PossibleMatchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.BadRequestException

@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@Service
class PrisonerSearchService(
  private val searchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: AuthenticationHolder,
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

  fun findPossibleMatchesBySearchCriteria(searchCriteria: PossibleMatchCriteria): List<Prisoner> {
    if (!searchCriteria.isValid()) {
      log.warn("Invalid search  - no criteria provided")
      throw BadRequestException("Invalid search  - please provide at least 1 search parameter")
    }
    val result = mutableListOf<Prisoner>()
    if (searchCriteria.nomsNumber != null) {
      result += queryBy(searchCriteria.nomsNumber.uppercase()) { fieldMatch("prisonerNumber", it) }.collect()
    }
    if (searchCriteria.pncNumber != null) {
      result += queryBy(searchCriteria.pncNumber) { pncMatch(it) }.collect()
    }
    if (searchCriteria.lastName != null && searchCriteria.dateOfBirth != null) {
      result += queryBy(searchCriteria) { nameMatchWithAliasesAndDob(it) }.collect()
    }
    return result.distinctBy { it.prisonerNumber }
  }

  fun findByReleaseDate(searchCriteria: ReleaseDateSearch, pageable: Pageable): Page<Prisoner> {
    searchCriteria.validate()
    queryBy(searchCriteria, pageable) { releaseDateMatch(it) } onMatch {
      customEventForFindByReleaseDate(searchCriteria, it.matches.size)
      return PageImpl(it.matches, pageable, it.totalHits)
    }
    return PageImpl(listOf(), pageable, 0L)
  }

  fun findByPrison(prisonId: String, pageable: Pageable, includeRestrictedPatients: Boolean = false): Page<Prisoner> {
    queryBy(
      prisonId,
      pageable,
    ) { if (includeRestrictedPatients) includeRestricted(it) else locationMatch(it) } onMatch {
      customEventForFindByPrisonId(prisonId, it.matches.size)
      return PageImpl(it.matches, pageable, it.totalHits)
    }
    return PageImpl(listOf(), pageable, 0L)
  }

  private fun validateSearchForm(searchCriteria: SearchCriteria) {
    if (!searchCriteria.isValid()) {
      log.warn("Invalid search  - no criteria provided")
      throw BadRequestException("Invalid search  - please provide at least 1 search parameter")
    }
  }

  private fun queryBy(
    searchCriteria: SearchCriteria,
    queryBuilder: (searchCriteria: SearchCriteria) -> BoolQueryBuilder?,
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

  private fun <T> queryBy(
    searchCriteria: T,
    queryBuilder: (searchCriteria: T) -> BoolQueryBuilder?,
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

  private fun queryBy(
    searchCriteria: ReleaseDateSearch,
    pageable: Pageable,
    queryBuilder: (searchCriteria: ReleaseDateSearch) -> BoolQueryBuilder?,
  ): GlobalResult {
    val query = queryBuilder(searchCriteria)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query)
        size(pageable.pageSize)
        from(pageable.offset.toInt())
        sort("_score")
        sort("prisonerNumber")
        trackTotalHits(true)
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val searchResults = searchClient.search(searchRequest)
      val prisonerMatches = getSearchResult(searchResults)
      return if (prisonerMatches.isEmpty()) {
        GlobalResult.NoMatch
      } else {
        GlobalResult.Match(
          prisonerMatches,
          searchResults.hits.totalHits?.value ?: 0,
        )
      }
    } ?: GlobalResult.NoMatch
  }

  private fun queryBy(
    prisonId: String,
    pageable: Pageable,
    queryBuilder: (prisonId: String) -> BoolQueryBuilder?,
  ): GlobalResult {
    val query = queryBuilder(prisonId)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query)
        size(pageable.pageSize)
        from(pageable.offset.toInt())
        sort("_score")
        sort("prisonerNumber")
        trackTotalHits(true)
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val searchResults = searchClient.search(searchRequest)
      val prisonerMatches = getSearchResult(searchResults)
      return if (prisonerMatches.isEmpty()) {
        GlobalResult.NoMatch
      } else {
        GlobalResult.Match(
          prisonerMatches,
          searchResults.hits.totalHits?.value ?: 0,
        )
      }
    } ?: GlobalResult.NoMatch
  }

  private fun matchByIds(criteria: PrisonerListCriteria<Any>): BoolQueryBuilder {
    return when (criteria) {
      is PrisonerNumbers -> shouldMatchOneOf("prisonerNumber", criteria.values())
      is BookingIds -> shouldMatchOneOf("bookingId", criteria.values())
    }
  }

  private fun idMatch(searchCriteria: SearchCriteria): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .mustMultiMatchKeyword(
          prisonerIdentifier?.canonicalPNCNumber(),
          "prisonerNumber",
          "bookingId",
          "pncNumber",
          "pncNumberCanonicalShort",
          "pncNumberCanonicalLong",
          "croNumber",
          "bookNumber",
        )
    }
  }

  private fun fieldMatch(field: String, value: String): BoolQueryBuilder {
    return QueryBuilders.boolQuery().must(field, value)
  }

  private fun pncMatch(pncNumber: String) = QueryBuilders.boolQuery()
    .mustMultiMatchKeyword(
      pncNumber.canonicalPNCNumber(),
      "pncNumber",
      "pncNumberCanonicalShort",
      "pncNumberCanonicalLong",
    )

  private fun releaseDateMatch(searchCriteria: ReleaseDateSearch): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .matchesDateRange(
          earliestReleaseDate,
          latestReleaseDate,
          "conditionalReleaseDate",
          "confirmedReleaseDate",
        )
        .filterWhenPresent("prisonId", searchCriteria.prisonIds?.toList())
    }
  }

  private fun includeRestricted(prisonId: String): BoolQueryBuilder =
    QueryBuilders.boolQuery()
      .mustMultiMatch(prisonId, "prisonId", "supportingPrisonId")

  private fun locationMatch(prisonId: String): BoolQueryBuilder =
    QueryBuilders.boolQuery().must("prisonId", prisonId)

  private fun nameMatch(searchCriteria: SearchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName),
            ),
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
                .mustWhenPresent("firstName", firstName),
            )
            .should(
              QueryBuilders.nestedQuery(
                "aliases",
                QueryBuilders.boolQuery()
                  .should(
                    QueryBuilders.boolQuery()
                      .mustWhenPresent("aliases.lastName", lastName)
                      .mustWhenPresent("aliases.firstName", firstName),
                  ),
                ScoreMode.Max,
              ),
            ),
        )
    }
  }

  private fun nameMatchWithAliasesAndDob(searchCriteria: PossibleMatchCriteria): BoolQueryBuilder? {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
                .mustWhenPresent("dateOfBirth", dateOfBirth),
            )
            .should(
              QueryBuilders.nestedQuery(
                "aliases",
                QueryBuilders.boolQuery()
                  .should(
                    QueryBuilders.boolQuery()
                      .mustWhenPresent("aliases.lastName", lastName)
                      .mustWhenPresent("aliases.firstName", firstName)
                      .mustWhenPresent("dateOfBirth", dateOfBirth),
                  ),
                ScoreMode.Max,
              ),
            ),
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

  fun findBy(criteria: PrisonerListCriteria<Any>): List<Prisoner> {
    with(criteria) {
      if (!isValid()) {
        log.warn("Invalid search  - no $type provided")
        throw BadRequestException("Invalid search  - please provide a minimum of 1 and a maximum of 1000 $type")
      }

      queryBy(criteria) { matchByIds(it) } onMatch {
        customEventForFindBy(type, values().size, it.matches.size)
        return it.matches
      }
      return emptyList()
    }
  }

  private fun customEventForFindBySearchCriteria(searchCriteria: SearchCriteria, numberOfResults: Int) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "lastname" to searchCriteria.lastName,
      "firstname" to searchCriteria.firstName,
      "prisonId" to searchCriteria.prisonIds.toString(),
      "prisonerIdentifier" to searchCriteria.prisonerIdentifier,
      "includeAliases" to searchCriteria.includeAliases.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }

  private fun customEventForFindByReleaseDate(searchCriteria: ReleaseDateSearch, numberOfResults: Int) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "earliestReleaseDate" to searchCriteria.earliestReleaseDate.toString(),
      "latestReleaseDateRange" to searchCriteria.latestReleaseDate.toString(),
      "prisonId" to searchCriteria.prisonIds.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByReleaseDate", propertiesMap, metricsMap)
  }

  private fun customEventForFindBy(type: String, prisonerListNumber: Int, numberOfResults: Int) {
    val logMap = mapOf(
      "username" to authenticationHolder.currentUsername(),
      "clientId" to authenticationHolder.currentClientId(),
      "numberOfPrisonerIds" to prisonerListNumber.toString(),
    )

    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByListOf$type", logMap, metricsMap)
  }

  private fun customEventForFindByPrisonId(
    prisonId: String,
    numberOfResults: Int,
  ) {
    val propertiesMap = mapOf(
      "prisonId" to prisonId,
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByPrisonId", propertiesMap, metricsMap)
  }
}

sealed class Result {
  object NoMatch : Result()
  data class Match(val matches: List<Prisoner>) : Result()
}

fun Result.collect() =
  when (this) {
    is Result.NoMatch -> emptyList<Prisoner>()
    is Result.Match -> matches
  }

inline infix fun Result.onMatch(block: (Result.Match) -> Nothing) =
  when (this) {
    is Result.NoMatch -> {
    }
    is Result.Match -> block(this)
  }

private fun BoolQueryBuilder.withDefaults(searchCriteria: SearchCriteria): BoolQueryBuilder {
  return this
    .filterWhenPresent("prisonId", searchCriteria.prisonIds)
}
