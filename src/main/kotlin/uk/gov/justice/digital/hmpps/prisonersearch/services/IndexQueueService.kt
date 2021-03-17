package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class IndexQueueService(
  @Autowired @Qualifier("awsSqsIndexASyncClient") private val awsSqsIndexASyncClient: AmazonSQSAsync,
  @Autowired @Qualifier("indexQueueUrl") private val indexQueueUrl: String,
  @Qualifier("awsSqsIndexClient") private val indexAwsSqsClient: AmazonSQS,
  @Qualifier("awsSqsIndexDlqClient") private val indexAwsSqsDlqClient: AmazonSQS,
  @Value("\${sqs.index.dlq.name}") private val indexDlqName: String,
  private val gson: Gson
) {

  val indexDlqUrl: String by lazy { indexAwsSqsDlqClient.getQueueUrl(indexDlqName).queueUrl }

  fun sendIndexRequestMessage(payload: PrisonerIndexRequest) {
    awsSqsIndexASyncClient.sendMessageAsync(SendMessageRequest(indexQueueUrl, gson.toJson(payload)))
  }

  fun clearAllMessages() {
    awsSqsIndexASyncClient.purgeQueueAsync(PurgeQueueRequest(indexQueueUrl))
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int {
    val queueAttributes = indexAwsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
  }

  fun getNumberOfMessagesCurrentlyOnIndexDLQ(): Int {
    val queueAttributes = indexAwsSqsDlqClient.getQueueAttributes(indexDlqUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
  }

  fun getIndexQueueStatus(): IndexQueueStatus {
    var queueAttributes = indexAwsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible")).attributes

    return IndexQueueStatus(
      queueAttributes["ApproximateNumberOfMessages"]?.toInt() ?: 0,
      getNumberOfMessagesCurrentlyOnIndexDLQ(),
      queueAttributes["ApproximateNumberOfMessagesNotVisible"]?.toInt() ?: 0
    )
  }
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
