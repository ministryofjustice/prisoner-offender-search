package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class IndexQueueStatusTest {

  private val indexQueueSqsClient = mock<SqsAsyncClient>()
  private val indexQueueSqsDlqClient = mock<SqsAsyncClient>()
  private val hmppsQueueService = mock<HmppsQueueService>()

  init {
    whenever(hmppsQueueService.findByQueueId("indexqueue")).thenReturn(
      HmppsQueue(
        "indexqueue",
        indexQueueSqsClient,
        "index-queue",
        indexQueueSqsDlqClient,
        "index-dlq"
      )
    )
    whenever(indexQueueSqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(
      CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-queue").build())
    )
    whenever(indexQueueSqsDlqClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(
      CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-dlq").build())
    )
  }

  private val indexQueueService = IndexQueueService(hmppsQueueService, Gson())

  private companion object {
    @JvmStatic
    fun activeTestSource() = listOf(
      Arguments.of(0, 0, 0, false),
      Arguments.of(1, 0, 0, true),
      Arguments.of(0, 1, 0, true),
      Arguments.of(0, 0, 1, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 1, 0, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 0, 1, true),
      Arguments.of(1, 1, 1, true)
    )
  }

  @ParameterizedTest
  @MethodSource("activeTestSource")
  fun `index queue status active`(
    messagesOnQueue: Int,
    messagesOnDlq: Int,
    messagesInFlight: Int,
    expectedActive: Boolean,
  ) {
    assertThat(IndexQueueStatus(messagesOnQueue, messagesOnDlq, messagesInFlight).active).isEqualTo(expectedActive)
  }

  @Test
  internal fun `async calls for queue status successfully complete`() {
    val indexQueueResult = GetQueueAttributesResponse.builder().attributes(
      mapOf(
        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES to "7",
        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE to "2",
      )
    ).build()

    val futureQueueAttributesResult = CompletableFuture.completedFuture(indexQueueResult)
    whenever(
      indexQueueSqsClient.getQueueAttributes(any<GetQueueAttributesRequest>())
    ).thenReturn(futureQueueAttributesResult)

    val indexDlqResult = GetQueueAttributesResponse.builder().attributes(
      mapOf(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES to "5")
    ).build()
    val futureDlqAttributesResult = CompletableFuture.completedFuture(indexDlqResult)

    whenever(indexQueueSqsDlqClient.getQueueAttributes(any<GetQueueAttributesRequest>()))
      .thenReturn(futureDlqAttributesResult)

    val queueStatus = indexQueueService.getIndexQueueStatus()
    assertThat(queueStatus.messagesOnQueue).isEqualTo(7)
    assertThat(queueStatus.messagesInFlight).isEqualTo(2)
    assertThat(queueStatus.messagesOnDlq).isEqualTo(5)
  }
}
