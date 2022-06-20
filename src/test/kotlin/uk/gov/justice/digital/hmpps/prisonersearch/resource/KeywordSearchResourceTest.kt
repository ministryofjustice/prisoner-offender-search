package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.SearchType

class KeywordSearchResourceTest : QueueIntegrationTest() {

  companion object {
    var initialiseSearchData = true
  }

  @BeforeEach
  fun setup() {

    if (initialiseSearchData) {

      setupIndexes()
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      initialiseSearchData = false
    }
  }

  @Test
  fun `access forbidden when no authority`() {
    webTestClient.post().uri("/keyword")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("LEI", "MDI")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `can perform a keyword search for ROLE_GLOBAL_SEARCH role`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a keyword search for ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a keyword search for ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `bad request when no filtering prison IDs provided`() {
    webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = emptyList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a keyword search for prisoner number`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "A7089EY", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can perform a match with OR words on incorrect prisoner number but correct name`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "X7089EY john smith", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can perform a keyword AND search on exact PNC number`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "12/394773H", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can perform a keyword AND search on PNC number with short year`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "15/1234S", prisonIds = listOf("WSI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A9999AA"),
    )
  }

  @Test
  fun `can perform a keyword AND search on PNC number with long year`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "2015/1234S", prisonIds = listOf("WSI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A9999AA"),
    )
  }

  @Test
  fun `can perform a keyword AND search on PNC number long year 20th century`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "1989/4444S", prisonIds = listOf("WSI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A9999AB"),
    )
  }

  @Test
  fun `can perform a keyword OR search on CRO number`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "29906/12J", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `cannot find by keyword AND when there is no exact match for all terms`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "trevor willis", prisonIds = listOf("MDI")),
      expectedCount = 0,
      expectedPrisoners = emptyList(),
    )
  }

  @Test
  fun `can perform a keyword EXACT phrase search on first name`() {
    keywordSearch(
      keywordRequest = KeywordRequest(exactPhrase = "john", prisonIds = listOf("LEI", "MDI")),
      expectedCount = 2,
      expectedPrisoners = listOf("A7089EZ", "A7089EY"),
    )
  }

  @Test
  fun `can perform a keyword EXACT phrase search on last name`() {
    keywordSearch(
      keywordRequest = KeywordRequest(exactPhrase = "smith", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can perform a keyword AND search on first and last name with multiple hits`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "sam jones", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 7,
      expectedPrisoners = listOf("A7090AB", "A7090AC", "A7090AD", "A7090BA", "A7090BB", "A7090BC", "A7090AF"),
    )
  }

  @Test
  fun `can perform a keyword AND search on mixed case names`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "SAm jONes", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 7,
      expectedPrisoners = listOf("A7090AB", "A7090AC", "A7090AD", "A7090BA", "A7090BB", "A7090BC", "A7090AF"),
    )
  }

  @Test
  fun `default order is by score, prison number`() {
    keywordSearch(
      keywordRequest = KeywordRequest(
        andWords = "jones",
        prisonIds = listOf("MDI", "AGI", "LEI"),
        type = SearchType.DEFAULT
      ),
      expectedCount = 9,
      expectedPrisoners = listOf(
        "A1090AA",
        "A7090AB",
        "A7090AC",
        "A7090AD",
        "A7090BA",
        "A7090BB",
        "A7090BC",
        "A7090AF",
        "A7090AA"
      ),
    )
  }

  @Test
  fun `establishment order is by name`() {
    keywordSearch(
      keywordRequest = KeywordRequest(
        andWords = "jones",
        prisonIds = listOf("MDI", "AGI", "LEI"),
        type = SearchType.ESTABLISHMENT
      ),
      expectedCount = 9,
      expectedPrisoners = listOf(
        "A7090AA", // JONES, ERIC
        "A7090AB", // JONES, SAM
        "A7090AC", // JONES, SAM
        "A7090AD", // JONES, SAM
        "A7090BA", // JONES, SAM
        "A7090BB", // JONES, SAM
        "A7090BC", // JONES, SAM
        "A7090AF", // JONES, SAM
        "A1090AA"  // JONES, ZAC
      ),
    )
  }

  @Test
  fun `can perform a keyword AND search on both names in aliases`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "danny colin", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `can perform a keyword AND search on multiple words narrowed down to one prisoner number`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "sam jones A7090AC", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AC"),
    )
  }

  @Test
  fun `can perform a keyword OR search on multiple words and include an unrelated prisoner number`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "sam jones A7089EZ", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 10,
      expectedPrisoners = listOf(
        "A1090AA",
        "A7089EZ",
        "A7090AB",
        "A7090AC",
        "A7090AD",
        "A7090BA",
        "A7090BB",
        "A7090BC",
        "A7090AA",
        "A7090AF"
      ),
    )
  }

  @Test
  fun `can perform a keyword OR search for all male gender prisoners in Moorland`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "male", prisonIds = listOf("MDI")),
      expectedCount = 7,
      expectedPrisoners = listOf("A1090AA", "A7089EY", "A7089FA", "A7089FB", "A7090AA", "A7090AB", "A7090BB"),
    )
  }

  @Test
  fun `can perform a keyword AND search on first name, last name and gender as female`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "sam jones female", prisonIds = listOf("AGI")),
      expectedCount = 2,
      expectedPrisoners = listOf("A7090AC", "A7090BC"),
    )
  }

  @Test
  fun `can perform a keyword OR search to match last name in alias`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "cordian", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EZ"),
    )
  }

  @Test
  fun `can perform a keyword OR search to match mixed case last name in alias`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "CORdian", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EZ"),
    )
  }

  @Test
  fun `can perform a combined AND and OR search for alias name and gender`() {
    keywordSearch(
      keywordRequest = KeywordRequest(andWords = "orange female", orWords = "jimbob jacks", prisonIds = listOf("LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `can perform a keyword OR search which returns no results as prison id is not matched`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "A7089EY", prisonIds = listOf("XXX")),
      expectedCount = 0,
      expectedPrisoners = emptyList(),
    )
  }

  @Test
  fun `can perform a keyword OR search and filter by a NOT term`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "sam", notWords = "female", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 4,
      expectedPrisoners = listOf("A7090AB", "A7090AD", "A7090BA", "A7090BB"),
    )
  }

  @Test
  fun `can perform a keyword OR search and filter by multiple NOT terms`() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "sam", notWords = "female christian", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7090BA"),
    )
  }

  @Test
  fun `can perform a keyword no-terms query to match all prisoners in one location`() {
    keywordSearch(
      keywordRequest = KeywordRequest(prisonIds = listOf("MDI")),
      expectedCount = 7,
      expectedPrisoners = listOf("A1090AA", "A7089EY", "A7089FA", "A7089FB", "A7090AA", "A7090AB", "A7090BB"),
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase prisoner number `() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "a7089Ey", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase CRO number `() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "29906/12j", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase PNC number `() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "12/394773h", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can perform a keyword OR search on lowercase PNC long year number `() {
    keywordSearch(
      keywordRequest = KeywordRequest(orWords = "2012/394773h", prisonIds = listOf("MDI")),
      expectedCount = 1,
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `can find those prisoner who are on remand in Moorland`() {
    keywordSearch(
      keywordRequest = KeywordRequest(exactPhrase = "remand", prisonIds = listOf("MDI")),
      expectedCount = 4,
      expectedPrisoners = listOf("A7089FA", "A7090AA", "A7090AB", "A7090BB"),
    )
  }
}
