package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service


@Service
class IndexQueueService(
  @Autowired @Qualifier("awsSqsIndexClient") private val awsSqsIndexClient: AmazonSQSAsync,
  @Value("\${sqs.index.queue.name}") private val indexQueueUrl: String,
  private val gson: Gson
) {
  private val queueUrl : String = awsSqsIndexClient.getQueueUrl(indexQueueUrl).queueUrl

  fun sendIndexRequestMessage(payload: IndexRequest) {
    awsSqsIndexClient.sendMessage(SendMessageRequest(queueUrl, gson.toJson(payload)))
  }
}

data class IndexRequest (
  val requestType: IndexRequestType,
  val indexData: String?
)
