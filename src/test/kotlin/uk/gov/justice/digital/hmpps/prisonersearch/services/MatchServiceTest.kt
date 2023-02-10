package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.lucene.search.TotalHits
import org.apache.lucene.search.TotalHits.Relation.EQUAL_TO
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchResponse.Clusters
import org.elasticsearch.action.search.SearchResponseSections
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.MultiMatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.boot.test.mock.mockito.MockBean
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.MatchedBy
import java.time.LocalDate

@JsonTest
internal class MatchServiceTest {
  private lateinit var service: MatchService

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @MockBean
  lateinit var searchClient: SearchClient

  @MockBean
  lateinit var indexStatusService: IndexStatusService

  @BeforeEach
  fun setUp() {
    service = MatchService(searchClient, indexStatusService, objectMapper)
  }

  @Nested
  inner class PartialNameDateOfBirthLenientMatchAttempt {

    @BeforeEach
    fun setUp() {
      whenever(searchClient.search(any()))
        .thenReturn(resultsOf()) // full match results
        .thenReturn(resultsOf()) // full match alias results
        .thenReturn(resultsOf()) // NOMS Number results
        .thenReturn(resultsOf()) // CRO Number results
        .thenReturn(resultsOf()) // PNC Number results
        .thenReturn(resultsOf()) // name match results
        .thenReturn(resultsOf()) // partial name match results
        .thenReturn(resultsOf(createPrisoner(lastName = "smith", bookingId = "99"), createPrisoner(lastName = "smith", bookingId = "88")))

      whenever(indexStatusService.getCurrentIndex()).thenReturn(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false))
    }

    @Test
    fun `names and date of birth variations will be added to query when present`() {
      service.match(
        MatchRequest(
          firstName = "john",
          lastName = "smith",
          dateOfBirth = LocalDate.of(1965, 7, 19),
          croNumber = "SF80/655108T",
          pncNumber = "2018/0003456X",
          nomsNumber = "G5555TT"
        )
      )

      val searchRequestCaptor = argumentCaptor<SearchRequest>()

      verify(searchClient, times(8)).search(searchRequestCaptor.capture())

      with(searchRequestCaptor.lastValue) {
        assertThat(mustNames()).containsExactlyInAnyOrderEntriesOf(
          mapOf(
            "lastName" to "smith"
          )
        )
        val query = source().query() as BoolQueryBuilder
        val dateMatches = query.must().filterIsInstance<BoolQueryBuilder>().first().should()
        val matchingDates = dateMatches.map { it.mustNames() }.flatMap { it.values }
        assertThat(matchingDates).containsExactlyInAnyOrder(
          "1965-01-19".toLocalDate(),
          "1965-02-19".toLocalDate(),
          "1965-03-19".toLocalDate(),
          "1965-04-19".toLocalDate(),
          "1965-05-19".toLocalDate(),
          "1965-06-19".toLocalDate(),
          "1965-07-18".toLocalDate(),
          "1965-07-19".toLocalDate(),
          "1965-07-20".toLocalDate(),
          "1965-08-19".toLocalDate(),
          "1965-09-19".toLocalDate(),
          "1965-10-19".toLocalDate(),
          "1965-11-19".toLocalDate(),
          "1965-12-19".toLocalDate()
        )

        assertThat(mustMultiMatchNames()).containsExactlyInAnyOrderEntriesOf(
          mapOf(
            "john" to listOf("firstName", "aliases.firstName").sorted()
          )
        )
      }
    }

    @Test
    fun `invalid date variations will not be tested`() {
      service.match(
        MatchRequest(
          firstName = "john",
          lastName = "smith",
          dateOfBirth = LocalDate.of(1965, 2, 28),
          croNumber = "SF80/655108T",
          pncNumber = "2018/0003456X",
          nomsNumber = "G5555TT"
        )
      )

      val searchRequestCaptor = argumentCaptor<SearchRequest>()
      verify(searchClient, times(8)).search(searchRequestCaptor.capture())

      val query = searchRequestCaptor.lastValue.source().query() as BoolQueryBuilder
      val dateMatches = query.must().filterIsInstance<BoolQueryBuilder>().first().should()
      val matchingDates = dateMatches.map { it.mustNames() }.flatMap { it.values }
      assertThat(matchingDates).containsExactlyInAnyOrder(
        "1965-01-28".toLocalDate(),
        "1965-02-27".toLocalDate(),
        "1965-02-28".toLocalDate(),
        "1965-03-28".toLocalDate(),
        "1965-04-28".toLocalDate(),
        "1965-05-28".toLocalDate(),
        "1965-06-28".toLocalDate(),
        "1965-07-28".toLocalDate(),
        "1965-08-28".toLocalDate(),
        "1965-09-28".toLocalDate(),
        "1965-10-28".toLocalDate(),
        "1965-11-28".toLocalDate(),
        "1965-12-28".toLocalDate()
      )
    }
  }

  @Nested
  inner class Results {

    @BeforeEach
    fun setUp() {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false))
    }

    val matchRequest = MatchRequest(
      firstName = "john",
      lastName = "smith",
      dateOfBirth = LocalDate.of(1965, 7, 19),
      croNumber = "SF80/655108T",
      pncNumber = "2018/0003456X",
      nomsNumber = "G5555TT"
    )

    @Test
    fun `will return matches`() {
      whenever(searchClient.search(any())).thenReturn(
        resultsOf(
          createPrisoner(lastName = "smith", bookingId = "99"),
          createPrisoner(lastName = "smith", bookingId = "88")
        )
      )

      val results = service.match(matchRequest)
      assertThat(results.matches).hasSize(2)
    }

    @Test
    fun `will return matched by ALL_SUPPLIED when matching all parameters`() {
      whenever(searchClient.search(any())).thenReturn(
        resultsOf(
          createPrisoner(lastName = "smith", bookingId = "99")
        )
      )

      val results = service.match(matchRequest)
      assertThat(results.matchedBy).isEqualTo(MatchedBy.ALL_SUPPLIED)
    }

    @Test
    fun `will return matched by ALL_SUPPLIED_ALIAS when matching all alias parameters`() {
      whenever(searchClient.search(any()))
        .thenReturn(resultsOf()) // full match
        .thenReturn(
          resultsOf(
            createPrisoner(lastName = "smith", bookingId = "99")
          )
        )

      val results = service.match(matchRequest)
      assertThat(results.matchedBy).isEqualTo(MatchedBy.ALL_SUPPLIED_ALIAS)
    }

    @Test
    fun `will return matched by HMPPS_KEY when matching on NOMS number`() {
      whenever(searchClient.search(any()))
        .thenReturn(resultsOf()) // full match
        .thenReturn(resultsOf()) // full match alias
        .thenReturn(
          resultsOf(
            createPrisoner(lastName = "smith", bookingId = "99")
          )
        )

      val results = service.match(matchRequest)
      assertThat(results.matchedBy).isEqualTo(MatchedBy.HMPPS_KEY)
    }

    @Test
    fun `will return matched by EXTERNAL_KEY when matching on CRO number`() {
      whenever(searchClient.search(any()))
        .thenReturn(resultsOf()) // full match
        .thenReturn(resultsOf()) // full match alias
        .thenReturn(resultsOf()) // NOMS number match
        .thenReturn(
          resultsOf(
            createPrisoner(lastName = "smith", bookingId = "99")
          )
        )

      val results = service.match(matchRequest)
      assertThat(results.matchedBy).isEqualTo(MatchedBy.EXTERNAL_KEY)
    }

    @Test
    fun `will return matched by EXTERNAL_KEY when matching on PNC number`() {
      whenever(searchClient.search(any()))
        .thenReturn(resultsOf()) // full match
        .thenReturn(resultsOf()) // full match alias
        .thenReturn(resultsOf()) // NOMS number match
        .thenReturn(resultsOf()) // CRO number match
        .thenReturn(
          resultsOf(
            createPrisoner(lastName = "smith", bookingId = "99")
          )
        )

      val results = service.match(matchRequest)
      assertThat(results.matchedBy).isEqualTo(MatchedBy.EXTERNAL_KEY)
    }
  }

  private fun resultsOf(vararg offenders: Prisoner): SearchResponse {
    val searchHits = offenders.map { SearchHit(it.bookingId!!.toInt()).apply { sourceRef(BytesArray(objectMapper.writeValueAsBytes(it))) } }
    val hits = SearchHits(searchHits.toTypedArray(), TotalHits(offenders.size.toLong(), EQUAL_TO), 10f)
    val searchResponseSections = SearchResponseSections(hits, null, null, false, null, null, 5)
    return SearchResponse(searchResponseSections, null, 8, 8, 0, 8, arrayOf(), Clusters.EMPTY)
  }

  private fun createPrisoner(lastName: String, bookingId: String): Prisoner {
    val prisoner = Prisoner()
    prisoner.lastName = lastName
    prisoner.bookingId = bookingId
    return prisoner
  }
}

private fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

fun SearchRequest.mustNames(): Map<String, Any> {
  val query = source().query() as BoolQueryBuilder
  return query.must().filterIsInstance<MatchQueryBuilder>().map { it.fieldName() to it.value() }.toMap()
}

fun SearchRequest.mustMultiMatchNames(): Map<Any, List<String>> {
  val query = source().query() as BoolQueryBuilder
  return query.must().filterIsInstance<MultiMatchQueryBuilder>().map { it.value() to it.fields().keys.toList().sorted() }.toMap()
}

fun QueryBuilder.mustNames(): Map<String, Any> {
  return when (this) {
    is BoolQueryBuilder -> this.must().filterIsInstance<MatchQueryBuilder>().map { it.fieldName() to it.value() }.toMap()
    else -> mapOf()
  }
}
