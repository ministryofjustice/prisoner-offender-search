package uk.gov.justice.digital.hmpps.prisonersearch

import org.junit.jupiter.api.BeforeEach

/**
 * Test class to initialise the standard set of search data only once.
 * Subclasses will get the same set of search data and we have implemented our own custom junit orderer to ensure that
 * no other type of tests run inbetween and cause a re-index to ruin our data.
 */
abstract class AbstractSearchDataIntegrationTest : QueueIntegrationTest() {

  private companion object {
    private var initialiseSearchData = true
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
}
