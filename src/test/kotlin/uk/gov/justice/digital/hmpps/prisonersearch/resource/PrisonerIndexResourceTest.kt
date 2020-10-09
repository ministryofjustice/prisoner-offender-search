package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexQueueStatus
import uk.gov.justice.digital.hmpps.prisonersearch.services.populateOffenderMessage

class PrisonerIndexResourceTest : QueueIntegrationTest() {

  @BeforeEach
  fun init() {
    resetStubs()
    setupIndexes()
    Mockito.reset(indexQueueService)
  }

  @Test
  fun `access forbidden when no authority`() {

    webTestClient.put().uri("/prisoner-index/build-index")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.put().uri("/prisoner-index/build-index")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `can index a prison with correct role`() {

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-status.startIndexTime").doesNotHaveJsonPath()
      .jsonPath("index-status.endIndexTime").doesNotHaveJsonPath()

    indexPrisoners()

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-status.startIndexTime").isNotEmpty
      .jsonPath("index-status.endIndexTime").doesNotHaveJsonPath()
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-status.startIndexTime").isNotEmpty
      .jsonPath("index-status.endIndexTime").isNotEmpty
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")
  }

  @Test
  fun `can cancel and re-index`() {

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")

    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/cancel-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")
  }


  @Test
  fun `can index a new prisoner`() {
    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")

    webTestClient.put().uri("/prisoner-index/index/prisoner/A5432AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/new_prisoner.json".readResourceAsText())

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("19")

  }

  @Test
  fun `both indexes are maintained whilst indexing but not once completed`() {

    //index B
    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    resetStubs()
    // Start indexing A
    indexPrisoners()

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("18")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")

    webTestClient.put().uri("/prisoner-index/index/prisoner/A5432AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("19")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("19")


    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.put().uri("/prisoner-index/index/prisoner/A5432AB")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("20")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("19")

  }

  @Test
  fun `can switch indexes`(){
    webTestClient.put().uri("/prisoner-index/switch-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.currentIndex").value<String> { currentIndex ->
        assertThat(currentIndex).isEqualTo(SyncIndex.INDEX_B.name)
  }}

  @Test
  fun `conflict returned if one index is rebuilding when trying to switch indexes`() {
    //index B
    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    resetStubs()
    // Start indexing A
    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/switch-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isEqualTo(409)
      .expectBody()
      .jsonPath("$.message").value<String> { message ->
        assertThat(message).isEqualTo("unable to switch indexes one is marked as in progress")
      }
  }

  @Test
  fun `can transfer items from dlq to normal queue`(){
    webTestClient.put().uri("/prisoner-index/transfer-index-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can purge items from dlq`(){
    webTestClient.put().uri("/prisoner-index/purge-index-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can transfer items from event dlq to normal queue`(){
    webTestClient.put().uri("/prisoner-index/transfer-event-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can purge items from event dlq `(){
    webTestClient.put().uri("/prisoner-index/purge-event-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Nested
  inner class HousekeepingBuildComplete {

    @Test
    fun `does not secure housekeeping endpoint`() {
      webTestClient.put().uri("/prisoner-index/index-queue-housekeeping")
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `will automatically complete build if ok`() {
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/index-queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")
    }

    @Test
    fun `will not complete if index build is still active`() {
      indexPrisoners()
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(1, 0, 0))

      webTestClient.put().uri("/prisoner-index/index-queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
    }
  }

  @Nested
  inner class HousekeepingIndexDlq {

    @Test
    fun `will add good DLQ messages to the index`() {
      indexPrisoners()

      awsSqsIndexDlqClient.sendMessage(indexDlqUrl, populateOffenderMessage("Z1234AA"))

      webTestClient.put().uri("/prisoner-index/index-queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
      await untilCallTo { prisonRequestCountFor("/api/offenders/Z1234AA") } matches { it == 1 }

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("19")

    }

    @Test
    fun `will only complete after 2nd housekeeping call if there are good messages on the DLQ`() {
      indexPrisoners()

      awsSqsIndexDlqClient.sendMessage(indexDlqUrl, populateOffenderMessage("Z1234AA"))

      webTestClient.put().uri("/prisoner-index/index-queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
      await untilCallTo { prisonRequestCountFor("/api/offenders/Z1234AA") } matches { it == 1 }

      webTestClient.put().uri("/prisoner-index/index-queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("19")
    }

  }

}

private fun String.readResourceAsText(): String = PrisonerIndexResourceTest::class.java.getResource(this).readText()
