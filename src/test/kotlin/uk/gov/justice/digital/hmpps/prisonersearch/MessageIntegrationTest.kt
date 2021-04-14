package uk.gov.justice.digital.hmpps.prisonersearch

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.verify
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.SearchCriteria

class MessageIntegrationTest : QueueIntegrationTest() {

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
  fun `will consume a new prisoner booking update`() {
    search(SearchCriteria("A7089FD", null, null), "/results/empty.json")

    val message = "/messages/offenderDetailsChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FD") } matches { it == 1 }

    search(SearchCriteria("A7089FD", null, null), "/results/search_results_merge1.json")
  }

  @Test
  fun `will handle a missing Offender Display ID`() {
    val message = "/messages/offenderUpdatedNoIdDisplay.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    verify(telemetryClient).trackEvent(eq("POSMissingOffenderDisplayId"), any(), isNull())
  }

  @Test
  fun `will consume a delete request and remove`() {
    search(SearchCriteria("A7089FC", null, null), "/results/search_results_to_delete.json")

    val message = "/messages/offenderDelete.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    Thread.sleep(500)
    search(SearchCriteria("A7089FC", null, null), "/results/empty.json")
  }

  @Test
  fun `will consume and check for merged record and remove`() {
    search(SearchCriteria("A7089FA", null, null), "/results/search_results_A7089FA.json")

    val message = "/messages/offenderMerge.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    Thread.sleep(500)
    search(SearchCriteria("A7089FB", null, null), "/results/search_results_A7089FB.json")
    search(SearchCriteria("A7089FA", null, null), "/results/empty.json")
  }

  @Test
  fun `will update both indexes for new prisoner events when rebuilding index`() {
    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")

    search(SearchCriteria("A7089FE", null, null), "/results/empty.json")

    resetStubs()
    // Start re-indexing
    indexPrisoners()

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("20")

    val message = "/messages/offenderDetailsNew.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
    Thread.sleep(500)

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("21")

    search(SearchCriteria("A7089FE", null, null), "/results/search_results_A7089FE.json")
  }
}

private fun String.readResourceAsText(): String {
  return MessageIntegrationTest::class.java.getResource(this).readText()
}
