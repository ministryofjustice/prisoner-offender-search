package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchedBy
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerMatch
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.PrisonerMatches
import java.time.DateTimeException
import java.time.LocalDate

@Service
class MatchService(
  private val elasticSearchClient: SearchClient,
  private val indexStatusService: IndexStatusService,
  private val mapper: ObjectMapper,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun match(matchRequest: MatchRequest): PrisonerMatches {
    matchBy(matchRequest) { fullMatch(it) } onPrisonerMatch { return PrisonerMatches(it.matches, MatchedBy.ALL_SUPPLIED) }
    matchBy(matchRequest) { fullMatchAlias(it) } onPrisonerMatch {
      return PrisonerMatches(
        it.matches,
        MatchedBy.ALL_SUPPLIED_ALIAS
      )
    }
    matchBy(matchRequest) { nomsNumber(it) } onPrisonerMatch { return PrisonerMatches(it.matches, MatchedBy.HMPPS_KEY) }
    matchBy(matchRequest) { croNumber(it) } onPrisonerMatch { return PrisonerMatches(it.matches, MatchedBy.EXTERNAL_KEY) }
    matchBy(matchRequest) { pncNumber(it) } onPrisonerMatch { return PrisonerMatches(it.matches, MatchedBy.EXTERNAL_KEY) }
    matchBy(matchRequest) { nameMatch(it) } onPrisonerMatch { return PrisonerMatches(it.matches, MatchedBy.NAME) }
    matchBy(matchRequest) { partialNameMatch(it) } onPrisonerMatch {
      return PrisonerMatches(
        it.matches,
        MatchedBy.PARTIAL_NAME
      )
    }
    matchBy(matchRequest) { partialNameMatchDateOfBirthLenient(it) } onPrisonerMatch {
      return PrisonerMatches(
        it.matches,
        MatchedBy.PARTIAL_NAME_DOB_LENIENT
      )
    }
    return PrisonerMatches()
  }

  private fun nomsNumber(matchRequest: MatchRequest): BoolQueryBuilder? {
    return matchRequest.nomsNumber.takeIf { !it.isNullOrBlank() }?.let {
      // NOMS number is a special case since a human has already matched so trust that judgement
      return QueryBuilders.boolQuery()
        .mustWhenPresent("prisonerNumber", it)
    }
  }

  private fun pncNumber(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return pncNumber.takeIf { !it.isNullOrBlank() }?.let {
        return QueryBuilders.boolQuery()
          .mustMultiMatchKeyword(it.canonicalPNCNumber(), "pncNumberCanonicalLong", "pncNumberCanonicalShort")
          .must(
            QueryBuilders.boolQuery()
              .shouldMultiMatch(lastName, "lastName", "aliases.lastName")
              .shouldMultiMatch(dateOfBirth, "dateOfBirth", "aliases.dateOfBirth")
          )
      }
    }
  }

  private fun croNumber(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return croNumber.takeIf { !it.isNullOrBlank() }?.let {
        return QueryBuilders.boolQuery()
          .mustKeyword(it.toUpperCase(), "croNumber")
          .must(
            QueryBuilders.boolQuery()
              .shouldMultiMatch(lastName, "lastName", "aliases.lastName")
              .shouldMultiMatch(dateOfBirth, "dateOfBirth", "aliases.dateOfBirth")
          )
      }
    }
  }

  private fun fullMatch(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return QueryBuilders.boolQuery()
        .mustKeyword(croNumber?.toUpperCase(), "croNumber")
        .mustMultiMatchKeyword(pncNumber?.canonicalPNCNumber(), "pncNumberCanonicalLong", "pncNumberCanonicalShort")
        .mustWhenPresent("prisonerNumber", nomsNumber)
        .apply {
          this.must(nameQuery(matchRequest))
        }
    }
  }

  private fun fullMatchAlias(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return QueryBuilders.boolQuery()
        .mustKeyword(croNumber?.toUpperCase(), "croNumber")
        .mustMultiMatchKeyword(pncNumber?.canonicalPNCNumber(), "pncNumberCanonicalLong", "pncNumberCanonicalShort")
        .mustWhenPresent("prisonerNumber", nomsNumber)
        .apply {
          this.must(aliasQuery(matchRequest))
        }
    }
  }

  private fun nameMatch(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(nameQuery(matchRequest))
            .should(aliasQuery(matchRequest))
        )
    }
  }

  private fun nameQuery(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return QueryBuilders.boolQuery()
        .should(
          QueryBuilders.boolQuery()
            .mustWhenPresent("lastName", lastName)
            .mustWhenPresent("firstName", firstName)
            .mustWhenPresent("dateOfBirth", dateOfBirth)
        )
    }
  }

  private fun aliasQuery(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return QueryBuilders.boolQuery()
        .should(
          QueryBuilders.nestedQuery(
            "aliases",
            QueryBuilders.boolQuery()
              .mustWhenPresent("aliases.lastName", lastName)
              .mustWhenPresent("aliases.firstName", firstName)
              .mustWhenPresent("aliases.dateOfBirth", dateOfBirth),
            ScoreMode.Max
          )
        )
    }
  }

  private fun partialNameMatch(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return QueryBuilders.boolQuery()
        .mustWhenPresent("lastName", lastName)
        .mustWhenPresent("dateOfBirth", dateOfBirth)
    }
  }

  private fun partialNameMatchDateOfBirthLenient(matchRequest: MatchRequest): BoolQueryBuilder? {
    with(matchRequest) {
      return dateOfBirth?.let {
        QueryBuilders.boolQuery()
          .mustMultiMatch(firstName, "firstName", "aliases.firstName")
          .mustWhenPresent("lastName", lastName)
          .mustMatchOneOf("dateOfBirth", allLenientDateVariations(dateOfBirth))
      }
    }
  }

  private fun allLenientDateVariations(date: LocalDate): List<LocalDate> {
    return swapMonthDay(date) + everyOtherValidMonth(date) + aroundDateInSameMonth(date)
  }

  private fun aroundDateInSameMonth(date: LocalDate) =
    listOf(date.minusDays(1), date.minusDays(-1), date).filter { it.month == date.month }

  private fun everyOtherValidMonth(date: LocalDate): List<LocalDate> =
    (1..12).filterNot { date.monthValue == it }.mapNotNull { setMonthDay(date, it) }

  private fun swapMonthDay(date: LocalDate): List<LocalDate> = try {
    listOf(LocalDate.of(date.year, date.dayOfMonth, date.monthValue))
  } catch (e: DateTimeException) {
    listOf()
  }

  private fun setMonthDay(date: LocalDate, monthValue: Int): LocalDate? = try {
    LocalDate.of(date.year, monthValue, date.dayOfMonth)
  } catch (e: DateTimeException) {
    null
  }

  private fun matchBy(matchRequest: MatchRequest, queryBuilder: (matchRequest: MatchRequest) -> BoolQueryBuilder?): PrisonerResult {
    val matchQuery = queryBuilder(matchRequest)
    return matchQuery?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(matchQuery.withDefaults(matchRequest))
      }
      val searchRequest = SearchRequest(arrayOf(getIndex()), searchSourceBuilder)
      val prisonerMatches = getSearchResult(elasticSearchClient.search(searchRequest))
      return if (prisonerMatches.isEmpty()) PrisonerResult.NoMatch else PrisonerResult.Match(prisonerMatches)
    } ?: PrisonerResult.NoMatch
  }

  private fun getIndex(): String {
    return indexStatusService.getCurrentIndex().currentIndex.indexName
  }

  private fun getSearchResult(response: SearchResponse): List<PrisonerMatch> {
    val searchHits = response.hits.hits.asList()
    log.debug("search found ${searchHits.size} hits")
    return searchHits.map { PrisonerMatch(toPrisonerDetail(it.sourceAsString)) }
  }

  private fun toPrisonerDetail(src: String) = mapper.readValue(src, Prisoner::class.java)
}

sealed class PrisonerResult {
  object NoMatch : PrisonerResult()
  data class Match(val matches: List<PrisonerMatch>) : PrisonerResult()
}

inline infix fun PrisonerResult.onPrisonerMatch(block: (PrisonerResult.Match) -> Nothing) {
  return when (this) {
    is PrisonerResult.NoMatch -> {
    }
    is PrisonerResult.Match -> block(this)
  }
}

private fun BoolQueryBuilder.withDefaults(matchRequest: MatchRequest): BoolQueryBuilder {
  return this
}
