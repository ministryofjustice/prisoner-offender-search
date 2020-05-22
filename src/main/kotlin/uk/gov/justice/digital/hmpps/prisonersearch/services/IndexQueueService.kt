package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service


@Service
class IndexQueueService(
  @Autowired @Qualifier("awsSqsIndexASyncClient") private val awsSqsIndexASyncClient: AmazonSQSAsync,
  @Autowired @Qualifier("indexQueueUrl") private val indexQueueUrl: String,
  private val gson: Gson
) {

  fun sendIndexRequestMessage(payload: PrisonerIndexRequest) {
    awsSqsIndexASyncClient.sendMessageAsync(SendMessageRequest(indexQueueUrl, gson.toJson(payload)))
  }

  fun clearAllMessages() {
    awsSqsIndexASyncClient.purgeQueueAsync(PurgeQueueRequest(indexQueueUrl))
  }
}

data class PrisonerIndexRequest (
  val requestType: IndexRequestType,
  val prisonerNumber: String? = null,
  val pageRequest: PageRequest? = null
)
