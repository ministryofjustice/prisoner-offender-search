package uk.gov.justice.digital.hmpps.prisonersearch

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
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
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventQueueSqsClient.sendMessage(eventQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A7089FD") } matches { it == 1 }

    search(SearchCriteria("A7089FD", null, null), "/results/search_results_merge1.json")
  }

  @Test
  fun `will add current incentive to prisoner documents`() {
    incentivesMockServer.stubCurrentIncentive(
      iepCode = "ENH",
      iepLevel = "Enhanced",
      iepTime = "2022-11-10T15:47:24.682335",
      nextReviewDate = "2023-11-19",
    )
    eventQueueSqsClient.sendMessage(eventQueueUrl, "/messages/offenderDetailsChanged.json".readResourceAsText())

    await untilAsserted {
      search(SearchCriteria("A7089FD", null, null))
        .jsonPath("$.[0].currentIncentive.level.code").isEqualTo("ENH")
        .jsonPath("$.[0].currentIncentive.level.description").isEqualTo("Enhanced")
        .jsonPath("$.[0].currentIncentive.dateTime").isEqualTo("2022-11-10T15:47:24")
        .jsonPath("$.[0].currentIncentive.nextReviewDate").isEqualTo("2023-11-19")
    }
  }

  @Test
  fun `will update incentive level when it changes`() {
    incentivesMockServer.stubCurrentIncentive(
      iepCode = "STD",
      iepLevel = "Standard",
      iepTime = "2022-11-10T15:47:24.682335",
      nextReviewDate = "2023-11-20",
    )
    hmppsDomainQueueSqsClient.sendMessage(hmppsDomainQueueUrl, "/messages/iepUpdated.json".readResourceAsText())

    await untilAsserted {
      search(SearchCriteria("A7089FD", null, null))
        .jsonPath("$.[0].currentIncentive.level.code").isEqualTo("STD")
        .jsonPath("$.[0].currentIncentive.level.description").isEqualTo("Standard")
        .jsonPath("$.[0].currentIncentive.dateTime").isEqualTo("2022-11-10T15:47:24")
        .jsonPath("$.[0].currentIncentive.nextReviewDate").isEqualTo("2023-11-20")
    }
  }

  @Test
  fun `will sync offender when the next review date changes`() {
    incentivesMockServer.stubCurrentIncentive(
      iepCode = "STD",
      iepLevel = "Standard",
      iepTime = "2022-11-11T15:47:24.682335",
      nextReviewDate = "2023-12-25",
    )
    hmppsDomainQueueSqsClient.sendMessage(hmppsDomainQueueUrl, "/messages/iepNextReviewDateUpdated.json".readResourceAsText())

    await untilAsserted {
      search(SearchCriteria("A7089FD", null, null))
        .jsonPath("$.[0].currentIncentive.level.code").isEqualTo("STD")
        .jsonPath("$.[0].currentIncentive.level.description").isEqualTo("Standard")
        .jsonPath("$.[0].currentIncentive.dateTime").isEqualTo("2022-11-11T15:47:24")
        .jsonPath("$.[0].currentIncentive.nextReviewDate").isEqualTo("2023-12-25")
    }
  }

  @Test
  fun `will sync offender when an assessment changes`() {
    val response = """
              {
                "offenderNo": "A7089FD",
                "bookingId": 1234,
                "csra": "VeryHigh",
                "firstName": "Altered",
                "lastName": "Csra",
                "dateOfBirth": "1980-01-01",
                "agencyId": "MDI",
                "activeFlag": true
              }
              """
    prisonMockServer.stubGetOffender(response)

    eventQueueSqsClient.sendMessage(eventQueueUrl, "/messages/assessmentUpdated.json".readResourceAsText())

    await untilAsserted {
      search(SearchCriteria("A7089FD", null, null))
        .jsonPath("$.[0].csra").isEqualTo("VeryHigh")
    }
  }

  @Test
  fun `will make a request for restricted patient data`() {
    val message = "/messages/offenderDetailsChangedForRP.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventQueueSqsClient.sendMessage(eventQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    await untilCallTo { prisonRequestCountFor("/api/offenders/A123ZZZ") } matches { it == 1 }

    restrictedPatientMockServer.verifyGetRestrictedPatientRequest("A123ZZZ")
  }

  @Test
  fun `will handle a missing Offender Display ID`() {
    val message = "/messages/offenderUpdatedNoIdDisplay.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventQueueSqsClient.sendMessage(eventQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    verify(telemetryClient).trackEvent(eq("POSMissingOffenderDisplayId"), any(), isNull())
  }

  @Test
  fun `will sync offender when a cro changes`() {
    val response = """
              {
                "offenderNo": "A7089FD",
                "bookingId": 1234,
                "firstName": "Altered",
                "lastName": "CRO",
                "dateOfBirth": "1980-01-01",
                "activeFlag": true,
                "identifiers": [
                  {
                    "type": "CRO",
                    "value": "CRO789",
                    "whenCreated": "2021-01-01T10:00:00"
                  }]
              }
              """
    prisonMockServer.stubGetOffender(response)

    eventQueueSqsClient.sendMessage(eventQueueUrl, "/messages/identifiersChanged.json".readResourceAsText())

    await untilAsserted {
      search(SearchCriteria("A7089FD", null, null))
        .jsonPath("$.[0].croNumber").isEqualTo("CRO789")
    }
  }

  @Test
  fun `will consume a delete request and remove`() {
    search(SearchCriteria("A7089FC", null, null), "/results/search_results_to_delete.json")

    val message = "/messages/offenderDelete.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventQueueSqsClient.sendMessage(eventQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    search(SearchCriteria("A7089FC", null, null), "/results/empty.json")
  }

  @Test
  fun `will consume and check for merged record and remove`() {
    search(SearchCriteria("A7089FA", null, null), "/results/search_results_A7089FA.json")

    val message = "/messages/offenderMerge.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventQueueSqsClient.sendMessage(eventQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
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
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("25")

    val message = "/messages/offenderDetailsNew.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventQueueSqsClient.sendMessage(eventQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("26")

    search(SearchCriteria("A7089FE", null, null), "/results/search_results_A7089FE.json")
  }
}
