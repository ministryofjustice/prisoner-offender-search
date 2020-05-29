package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.amazonaws.services.sqs.model.SendMessageResult
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service


@Service
class IndexQueueService(
  @Autowired @Qualifier("awsSqsIndexASyncClient") private val awsSqsIndexASyncClient: AmazonSQSAsync,
  @Autowired @Qualifier("indexQueueUrl") private val indexQueueUrl: String,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient)
{

  fun sendIndexRequestMessage(payload: PrisonerIndexRequest) {
    awsSqsIndexASyncClient.sendMessageAsync(SendMessageRequest(indexQueueUrl, gson.toJson(payload)),
      object : AsyncHandler<SendMessageRequest?, SendMessageResult?> {
      override fun onError(exception: Exception) {
        telemetryClient.trackEvent(
          "POSIndexRequestMessageFailure",
          mapOf(
            "requestType" to payload.requestType.name,
            "prisonerNumber" to payload.prisonerNumber,
            "pageRequest" to payload.pageRequest.toString()
          ),
          null)
      }
      override fun onSuccess(request: SendMessageRequest?, sendMessageResult: SendMessageResult?) {}
      }
    )
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
