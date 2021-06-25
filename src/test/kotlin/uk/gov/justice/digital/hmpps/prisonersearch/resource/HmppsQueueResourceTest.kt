package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.PurgeQueueResult
import uk.gov.justice.hmpps.sqs.RetryDlqResult

class HmppsQueueResourceTest : QueueIntegrationTest() {

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class SecureEndpoints {
    private fun secureEndpoints() =
      listOf(
        "/queue-admin/purge-queue/any",
        "/queue-admin/retry-dlq/any",
      )

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires a valid authentication token`(uri: String) {
      webTestClient.put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
    }

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires the correct role`(uri: String) {
      webTestClient.put()
        .uri(uri)
        .headers(setAuthorisation(roles = listOf()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `purge - satisfies the correct role`() {
      val queueName = "any queue"
      val dlqName = "any dlq"
      doReturn(PurgeQueueRequest(dlqName, awsSqsClient, "any url")).whenever(hmppsQueueService).findQueueToPurge(any())
      doReturn(HmppsQueue("any queue id", awsSqsClient, queueName, awsSqsDlqClient, dlqName)).whenever(hmppsQueueService).findByDlqName(dlqName)
      doReturn(PurgeQueueResult(0)).whenever(hmppsQueueService).purgeQueue(any())

      webTestClient.put()
        .uri("/queue-admin/purge-queue/$dlqName")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      verify(hmppsQueueService).purgeQueue(
        check {
          assertThat(it.queueName).isEqualTo(dlqName)
        }
      )
    }

    @Test
    internal fun `transfer - satisfies the correct role`() {
      val queueName = "any queue"
      val dlqName = "any dlq"
      doReturn(HmppsQueue("any queue id", awsSqsClient, queueName, awsSqsDlqClient, dlqName)).whenever(hmppsQueueService).findByDlqName(dlqName)
      doReturn(RetryDlqResult(0, listOf())).whenever(hmppsQueueService).retryDlqMessages(any())

      webTestClient.put()
        .uri("/queue-admin/retry-dlq/$dlqName")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      verify(hmppsQueueService).retryDlqMessages(
        check {
          assertThat(it.hmppsQueue.dlqName).isEqualTo(dlqName)
        }
      )
    }
  }
}
