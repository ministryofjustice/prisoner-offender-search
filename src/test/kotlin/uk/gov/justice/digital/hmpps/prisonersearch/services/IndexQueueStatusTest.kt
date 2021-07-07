package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class IndexQueueStatusTest {

  private val indexQueueSqsClient = mock<AmazonSQSAsync>()
  private val indexQueueSqsDlqClient = mock<AmazonSQSAsync>()
  private val hmppsQueueService = mock<HmppsQueueService>()

  init {
    whenever(hmppsQueueService.findByQueueId("indexqueue")).thenReturn(HmppsQueue("indexqueue", indexQueueSqsClient, "index-queue", indexQueueSqsDlqClient, "index-dlq"))
    whenever(indexQueueSqsClient.getQueueUrl("index-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-queue"))
    whenever(indexQueueSqsDlqClient.getQueueUrl("index-dlq")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-dlq"))
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
    expectedActive: Boolean
  ) {
    assertThat(IndexQueueStatus(messagesOnQueue, messagesOnDlq, messagesInFlight).active).isEqualTo(expectedActive)
  }

  @Test
  internal fun `async calls for queue status successfully complete`() {
    val indexQueueResult = GetQueueAttributesResult()
    indexQueueResult.attributes["ApproximateNumberOfMessages"] = "7"
    indexQueueResult.attributes["ApproximateNumberOfMessagesNotVisible"] = "2"

    val futureQueueAttributesResult = CompletableFuture.completedFuture(indexQueueResult)
    whenever(
      indexQueueSqsClient.getQueueAttributesAsync(
        "arn:eu-west-1:index-queue",
        listOf(
          "ApproximateNumberOfMessages",
          "ApproximateNumberOfMessagesNotVisible"
        )
      )
    ).thenReturn(futureQueueAttributesResult)

    val indexDlqResult = GetQueueAttributesResult()
    indexDlqResult.attributes["ApproximateNumberOfMessages"] = "5"
    val futureDlqAttributesResult = CompletableFuture.completedFuture(indexDlqResult)

    whenever(indexQueueSqsDlqClient.getQueueAttributesAsync("arn:eu-west-1:index-dlq", listOf("ApproximateNumberOfMessages")))
      .thenReturn(futureDlqAttributesResult)

    val queueStatus = indexQueueService.getIndexQueueStatus()
    assertThat(queueStatus.messagesOnQueue).isEqualTo(7)
    assertThat(queueStatus.messagesInFlight).isEqualTo(2)
    assertThat(queueStatus.messagesOnDlq).isEqualTo(5)
  }
}
