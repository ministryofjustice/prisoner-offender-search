package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.github.tomakehurst.wiremock.client.WireMock
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
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexQueueStatus
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.LocalDate

class PrisonerIndexResourceTest : QueueIntegrationTest() {

  private val indexCount = 2

  @BeforeEach
  fun init() {
    elasticsearchClient.deleteByQuery(
      DeleteByQueryRequest(SyncIndex.INDEX_A.indexName).setQuery(QueryBuilders.matchAllQuery()),
      RequestOptions.DEFAULT,
    )
    elasticsearchClient.deleteByQuery(
      DeleteByQueryRequest(SyncIndex.INDEX_B.indexName).setQuery(QueryBuilders.matchAllQuery()),
      RequestOptions.DEFAULT,
    )
    resetStubs()
    setupIndexes()

    // Use reduced set of prisoners for speed in this test class
    whenever(indexProperties.pageSize).thenReturn(100) // use a different page size to avoid matching a "getId.." file
    StubOffendersIds()
  }

  private fun StubOffendersIds() {
    prisonMockServer.stubFor(
      WireMock.get(
        WireMock.urlEqualTo("/api/offenders/ids"),
      )
        .withHeader("Page-Limit", WireMock.equalTo("100"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withHeader("Total-Records", indexCount.toString())
            .withBody("""[{"offenderNumber": "A7089EY"}, {"offenderNumber": "A7089EZ"}]"""),
        ),
    )
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
  fun `can index a prisoner`() {
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
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo(0)
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
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo(0)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount + 1)

    webTestClient.put().uri("/prisoner-index/index/prisoner/A0000AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isNotFound

    // Add a non-existent prisoner to the index
    prisonerIndexService.reindex(
      OffenderBooking(
        "B0001AA",
        "JOE",
        "NOTINNOMIS",
        LocalDate.parse("1975-01-01"),
        true,
        12345678,
        "B1234",
      ),
    )

    webTestClient.put().uri("/prisoner-index/index/prisoner/B0001AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `both indexes are maintained whilst indexing but not once completed`() {
    // index B
    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

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
    webTestClient.put().uri("/queue-admin/retry-dlq/$indexDlqName")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can purge items from dlq`() {
    webTestClient.put().uri("/queue-admin/purge-queue/$indexDlqName")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can transfer items from event dlq to normal queue`() {
    webTestClient.put().uri("/queue-admin/retry-dlq/$eventDlqName")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can purge items from event dlq `() {
    webTestClient.put().uri("/queue-admin/purge-queue/$eventDlqName")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes: One is marked as in progress or in error")
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

      // Start indexing A
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/switch-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes: One is marked as in progress or in error")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes: One is marked as in progress or in error")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to switch indexes: One is marked as in progress or in error")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index reason: Index is marked as in progress or in error")
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

      // Start indexing A
      indexPrisoners()

      webTestClient.put().uri("/prisoner-index/build-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index reason: Index is marked as in progress or in error")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index reason: Index is marked as in progress or in error")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable to build index reason: Index is marked as in progress or in error")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable mark index as complete: Index is in error")
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
        .jsonPath("$.developerMessage").value<String> { message ->
          assertThat(message).isEqualTo("Unable mark index as complete: Index is in error")
        }
    }

    private fun updateIndexErrorStatus(inProgress: Boolean, inError: Boolean) {
      val resetIndexStatus = Request("PUT", "/offender-index-status/_doc/STATUS")
      resetIndexStatus.setJsonEntity(gson.toJson(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, inProgress, inError)))
      elasticsearchClient.lowLevelClient.performRequest(resetIndexStatus)
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
      whenever(indexProperties.completeThreshold).thenReturn(indexCount + 1L)

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
      whenever(indexProperties.completeThreshold).thenReturn(indexCount - 1L)

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
  inner class QueueAdminIndexDlq {

    @Test
    fun `will add good DLQ messages to the index`() {
      indexPrisoners()

      indexQueueSqsDlqClient.sendMessage(indexDlqUrl, populateOffenderMessage("Z1234AA"))

      webTestClient.put().uri("/queue-admin/retry-dlq/$indexDlqName")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
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
        .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo(0) // current index unaffected
        .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo(indexCount + 1) // other index receives dlq reindex request
    }

    @Test
    fun `will only complete after housekeeping call if there are good messages on the DLQ`() {
      indexPrisoners()

      indexQueueSqsDlqClient.sendMessage(indexDlqUrl, populateOffenderMessage("Z1234AA"))

      webTestClient.put().uri("/queue-admin/retry-dlq/$indexDlqName")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
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
  inner class QueueAdminEventDlq {
    @Test
    fun `will add good messages to the index`() {
      indexPrisoners()
      webTestClient.put().uri("/prisoner-index/mark-complete")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      eventQueueSqsDlqClient.sendMessage(eventDlqUrl, offenderChangedMessage("Z1234AA"))

      webTestClient.put().uri("/queue-admin/retry-dlq/$eventDlqName")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
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
      eventQueueSqsDlqClient.sendMessage(eventDlqUrl, offenderChangedMessage("Z1235AB"))

      webTestClient.put().uri("/queue-admin/retry-dlq/$eventDlqName")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
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

fun populateOffenderMessage(offenderNumber: String) =
  """
  {
    "requestType": "OFFENDER",
    "prisonerNumber":"$offenderNumber"
  }
  """.trimIndent()

fun offenderChangedMessage(offenderNumber: String) =
  """
    {
  "Type": "Notification",
  "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
  "TopicArn": "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
  "Message": "{\"eventType\":\"OFFENDER-UPDATED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"offenderIdDisplay\":\"$offenderNumber\",\"nomisEventType\":\"OFFENDER-UPDATED\"}",
  "Timestamp": "2020-02-25T11:25:16.169Z",
  "SignatureVersion": "1",
  "Signature": "h5p3FnnbsSHxj53RFePh8HR40cbVvgEZa6XUVTlYs/yuqfDsi17MPA+bX4ijKmmTT2l6xG2xYhcmRAbJWQ4wrwncTBm2azgiwSO5keRNWYVdiC0rI484KLZboP1SDsE+Y7hOU/R0dz49q7+0yd+QIocPteKB/8xG7/6kjGStAZKf3cEdlxOwLhN+7RU1Yk2ENuwAJjVRtvlAa76yKB3xvL2hId7P7ZLmHGlzZDNZNYxbg9C8HGxteOzZ9ZeeQsWDf9jmZ+5+7dKXQoW9LeqwHxEAq2vuwSZ8uwM5JljXbtS5w1P0psXPYNoin2gU1F5MDK8RPzjUtIvjINx08rmEOA==",
  "SigningCertURL": "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-a86cb10b4e1f29c941702d737128f7b6.pem",
  "UnsubscribeURL": "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:92545cfe-de5d-43e1-8339-c366bf0172aa",
  "MessageAttributes": {
    "eventType": {
      "Type": "String",
      "Value": "OFFENDER-UPDATED"
    },
    "id": {
      "Type": "String",
      "Value": "cb4645f2-d0c1-4677-806a-8036ed54bf69"
    },
    "contentType": {
      "Type": "String",
      "Value": "text/plain;charset=UTF-8"
    },
    "timestamp": {
      "Type": "Number.java.lang.Long",
      "Value": "1582629916147"
    }
  }
}
  """.trimIndent()
