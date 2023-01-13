package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException

@Service
class IndexQueueService(
  hmppsQueueService: HmppsQueueService,
  private val gson: Gson
) {

  private val indexQueue = hmppsQueueService.findByQueueId("indexqueue") ?: throw MissingQueueException("HmppsQueue indexqueue not found")

  private val indexQueueSqsClient = indexQueue.sqsClient
  private val indexQueueSqsDlqClient = indexQueue.sqsDlqClient
  private val indexQueueUrl = indexQueue.queueUrl
  private val indexDlqUrl = indexQueue.dlqUrl as String

  fun sendIndexRequestMessage(payload: PrisonerIndexRequest) {
    indexQueueSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(indexQueueUrl).messageBody(gson.toJson(payload)).build()
    )
  }

  fun clearAllMessages() {
    indexQueueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(indexQueueUrl).build())
  }

  fun getIndexQueueStatus(): IndexQueueStatus {
    val queueAttributesAsyncFuture = indexQueueSqsClient.getQueueAttributes(
      GetQueueAttributesRequest.builder().queueUrl(indexQueueUrl).attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES, APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE).build()
    )
    val dlqAttributesAsyncFuture = indexQueueSqsDlqClient?.getQueueAttributes(
      GetQueueAttributesRequest.builder().queueUrl(indexDlqUrl).attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES).build()
    )

    return IndexQueueStatus(
      messagesOnQueue = queueAttributesAsyncFuture.get().attributes()[APPROXIMATE_NUMBER_OF_MESSAGES].toIntOrZero(),
      messagesInFlight = queueAttributesAsyncFuture.get().attributes()[APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE].toIntOrZero(),
      messagesOnDlq = dlqAttributesAsyncFuture?.get()?.attributes()?.get(APPROXIMATE_NUMBER_OF_MESSAGES).toIntOrZero(),
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
