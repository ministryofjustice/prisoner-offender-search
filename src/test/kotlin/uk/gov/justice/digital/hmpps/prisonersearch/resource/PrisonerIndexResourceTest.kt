package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.client.Request
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexQueueStatus
import uk.gov.justice.digital.hmpps.prisonersearch.services.offenderChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.services.populateOffenderMessage

class PrisonerIndexResourceTest : QueueIntegrationTest() {

  private val indexCount = 20

  @BeforeEach
  fun init() {
    elasticSearchClient.deleteByQuery(
      DeleteByQueryRequest(SyncIndex.INDEX_A.indexName).setQuery(QueryBuilders.matchAllQuery()),
      RequestOptions.DEFAULT
    )
    elasticSearchClient.deleteByQuery(
      DeleteByQueryRequest(SyncIndex.INDEX_B.indexName).setQuery(QueryBuilders.matchAllQuery()),
      RequestOptions.DEFAULT
    )
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
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)

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
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)
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
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)
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
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)

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
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount + 1)
  }

  @Test
  fun `both indexes are maintained whilst indexing but not once completed`() {

    // index B
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
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo(indexCount)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)

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
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo(indexCount + 1)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount + 1)

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
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo(indexCount + 2)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount + 1)
  }

  @Test
  fun `can switch indexes`() {
    webTestClient.put().uri("/prisoner-index/switch-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.currentIndex").value<String> { currentIndex ->
        assertThat(currentIndex).isEqualTo(SyncIndex.INDEX_B.name)
      }
  }

  @Test
  fun `can transfer items from dlq to normal queue`() {
    webTestClient.put().uri("/prisoner-index/transfer-index-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can purge items from dlq`() {
    webTestClient.put().uri("/prisoner-index/purge-index-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can transfer items from event dlq to normal queue`() {
    webTestClient.put().uri("/prisoner-index/transfer-event-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can purge items from event dlq `() {
    webTestClient.put().uri("/prisoner-index/purge-event-dlq")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Nested
  inner class BuildErrorConflict {
    @Test
    fun `conflict returned if index is rebuilding when trying to switch indexes`() {
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/switch-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes - one is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if alternate index is rebuilding when trying to switch indexes`() {
      // index B
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
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes - one is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if index is rebuilding and inError when trying to switch indexes`() {
      indexPrisoners()
      updateIndexErrorStatus(inProgress = true, inError = true)

      webTestClient.put().uri("/prisoner-index/switch-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes - one is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if index is inError when trying to switch indexes`() {
      indexPrisoners()
      updateIndexErrorStatus(inProgress = false, inError = true)

      webTestClient.put().uri("/prisoner-index/switch-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes - one is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if index is rebuilding when trying to initiate another build`() {
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/build-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index - it is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if alternate index is rebuilding trying to initiate another build`() {
      // index B
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      resetStubs()
      // Start indexing A
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/build-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index - it is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if index is rebuilding and inError when trying to initiate another build`() {
      indexPrisoners()
      updateIndexErrorStatus(inProgress = true, inError = true)

      webTestClient.put().uri("/prisoner-index/build-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index - it is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if index is inError when trying to initiate another build`() {
      indexPrisoners()
      updateIndexErrorStatus(inProgress = false, inError = true)

      webTestClient.put().uri("/prisoner-index/build-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index - it is marked as in progress or in error")
        }
    }

    @Test
    fun `conflict returned if index is inError when trying to markComplete`() {
      indexPrisoners()
      updateIndexErrorStatus(inProgress = false, inError = true)

      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to marked index complete as it is in error")
        }
    }

    @Test
    fun `conflict returned if index is rebuilding and inError when trying to markComplete`() {
      indexPrisoners()
      updateIndexErrorStatus(inProgress = true, inError = true)

      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.message").value<String> { message ->
          assertThat(message).isEqualTo("Unable to marked index complete as it is in error")
        }
    }

    private fun updateIndexErrorStatus(inProgress: Boolean, inError: Boolean) {
      val resetIndexStatus = Request("PUT", "/offender-index-status/_doc/STATUS")
      resetIndexStatus.setJsonEntity(gson.toJson(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, inProgress, inError)))
      elasticSearchClient.lowLevelClient.performRequest(resetIndexStatus)
    }
  }

  @Nested
  inner class MarkBuildComplete {

    @Test
    fun `will automatically complete build if ok`() {
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
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)
    }

    @Test
    fun `will not complete if index size not reached threshold`() {
      indexPrisoners()
      whenever(indexProperties.completeThreshold).thenReturn(21)

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
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
        .jsonPath("index-status.inProgress").isEqualTo(true)
    }

    @Test
    fun `will complete if index size not reached threshold but ignoring threshold`() {
      indexPrisoners()
      whenever(indexProperties.completeThreshold).thenReturn(21)

      webTestClient.put().uri("/prisoner-index/mark-complete?ignoreThreshold=true")
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
        .jsonPath("index-status.inProgress").isEqualTo(false)
    }
  }

  @Nested
  inner class HousekeepingBuildComplete {

    @Test
    fun `does not secure housekeeping endpoint`() {
      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `will automatically complete build if ok`() {
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)
    }

    @Test
    fun `will not complete if index build is still active`() {
      indexPrisoners()
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(1, 0, 0))

      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
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

      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
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
        .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo(indexCount + 1)
    }

    @Test
    fun `will only complete after 2nd housekeeping call if there are good messages on the DLQ`() {
      indexPrisoners()

      awsSqsIndexDlqClient.sendMessage(indexDlqUrl, populateOffenderMessage("Z1234AA"))

      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
      await untilCallTo { prisonRequestCountFor("/api/offenders/Z1234AA") } matches { it == 1 }

      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount + 1)
    }
  }

  @Nested
  inner class HousekeepingEventDlq {
    @Test
    fun `will add good messages to the index`() {
      indexPrisoners()
      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      awsSqsDlqClient.sendMessage(dlqUrl, offenderChangedMessage("Z1234AA"))

      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
      await untilCallTo { prisonRequestCountFor("/api/offenders/Z1234AA") } matches { it == 1 }

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount + 1)
    }

    @Test
    fun `will not add bad messages to the index`() {
      indexPrisoners()
      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      // this offender is not stubbed in prison API - hence the message is "bad"
      awsSqsDlqClient.sendMessage(dlqUrl, offenderChangedMessage("Z1235AB"))

      webTestClient.put().uri("/prisoner-index/queue-housekeeping")
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
      await untilCallTo { prisonRequestCountFor("/api/offenders/Z1235AB") } matches { it == 1 }

      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount)
    }
  }
}

private fun String.readResourceAsText(): String = PrisonerIndexResourceTest::class.java.getResource(this).readText()
