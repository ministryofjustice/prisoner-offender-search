package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.gson.Gson
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException

@Service
class IndexQueueService(
  hmppsQueueService: HmppsQueueService,
  private val gson: Gson
) {

  private val indexQueue = hmppsQueueService.findByQueueId("indexqueue") ?: throw MissingQueueException("HmppsQueue indexqueue not found")

  private val indexQueueSqsClient = indexQueue.sqsClient as AmazonSQSAsync
  private val indexQueueSqsDlqClient = indexQueue.sqsDlqClient as AmazonSQSAsync
  private val indexQueueUrl = indexQueue.queueUrl
  private val indexDlqUrl = indexQueue.dlqUrl as String

  fun sendIndexRequestMessage(payload: PrisonerIndexRequest) {
    indexQueueSqsClient.sendMessageAsync(SendMessageRequest(indexQueueUrl, gson.toJson(payload)))
  }

  fun clearAllMessages() {
    indexQueueSqsClient.purgeQueueAsync(PurgeQueueRequest(indexQueueUrl))
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int {
    val queueAttributes = indexQueueSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"].toIntOrZero()
  }

  fun getNumberOfMessagesCurrentlyOnIndexDLQ(): Int {
    val queueAttributes = indexQueueSqsDlqClient.getQueueAttributes(indexDlqUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"].toIntOrZero()
  }

  fun getIndexQueueStatus(): IndexQueueStatus {
    val queueAttributesAsyncFuture = indexQueueSqsClient.getQueueAttributesAsync(indexQueueUrl, listOf("ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"))
    val dlqAttributesAsyncFuture = indexQueueSqsDlqClient.getQueueAttributesAsync(indexDlqUrl, listOf("ApproximateNumberOfMessages"))

    return IndexQueueStatus(
      messagesOnQueue = queueAttributesAsyncFuture.get().attributes["ApproximateNumberOfMessages"].toIntOrZero(),
      messagesInFlight = queueAttributesAsyncFuture.get().attributes["ApproximateNumberOfMessagesNotVisible"].toIntOrZero(),
      messagesOnDlq = dlqAttributesAsyncFuture.get().attributes["ApproximateNumberOfMessages"].toIntOrZero(),
    )
  }
  private fun String?.toIntOrZero() =
    this?.toInt() ?: 0
}

data class PrisonerIndexRequest(
  val requestType: IndexRequestType?,
  val prisonerNumber: String? = null,
  val pageRequest: PageRequest? = null
)

data class IndexQueueStatus(val messagesOnQueue: Int, val messagesOnDlq: Int, val messagesInFlight: Int) {
  val active
    get() = messagesOnQueue > 0 || messagesOnDlq > 0 || messagesInFlight > 0
}
