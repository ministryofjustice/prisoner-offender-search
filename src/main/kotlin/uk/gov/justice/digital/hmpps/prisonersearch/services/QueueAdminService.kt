package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.config.TelemetryEvents

@Service
class QueueAdminService(
  @Qualifier("awsSqsIndexClient") private val indexAwsSqsClient: AmazonSQS,
  @Qualifier("awsSqsIndexDlqClient") private val indexAwsSqsDlqClient: AmazonSQS,
  @Qualifier("awsSqsClient") private val eventAwsSqsClient: AmazonSQS,
  @Qualifier("awsSqsDlqClient") private val eventAwsSqsDlqClient: AmazonSQS,
  private val indexQueueService: IndexQueueService,
  private val telemetryClient: TelemetryClient,
  @Value("\${sqs.index.queue.name}") private val indexQueueName: String,
  @Value("\${sqs.index.dlq.name}") private val indexDlqName: String,
  @Value("\${sqs.queue.name}") private val eventQueueName: String,
  @Value("\${sqs.dlq.name}") private val eventDlqName: String,
  private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val indexQueueUrl: String by lazy { indexAwsSqsClient.getQueueUrl(indexQueueName).queueUrl }
  val indexDlqUrl: String by lazy { indexAwsSqsDlqClient.getQueueUrl(indexDlqName).queueUrl }
  val eventQueueUrl: String by lazy { eventAwsSqsClient.getQueueUrl(eventQueueName).queueUrl }
  val eventDlqUrl: String by lazy { eventAwsSqsDlqClient.getQueueUrl(eventDlqName).queueUrl }

  fun clearAllIndexQueueMessages() {
    indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue()
      .takeIf { it > 0 }
      ?.also { total ->
        indexAwsSqsClient.purgeQueue(PurgeQueueRequest(indexQueueUrl))
        telemetryClient.trackEvent(
          TelemetryEvents.PURGED_INDEX_QUEUE.name,
          mapOf("messages-on-queue" to total.toString()),
          null
        )
        log.info("Clear all messages on index queue - found $total message(s)")
      }
      ?: also {
        log.info("No messages to clear on index queue")
      }
  }

  fun clearAllDlqMessagesForIndex() {
    indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ()
      .takeIf { it > 0 }
      ?.also { total ->
        indexAwsSqsDlqClient.purgeQueue(PurgeQueueRequest(indexDlqUrl))
        telemetryClient.trackEvent(
          TelemetryEvents.PURGED_INDEX_DLQ.name,
          mapOf("messages-on-queue" to total.toString()),
          null
        )
        log.info("Clear all messages on index dead letter queue - found $total message(s)")
      }
      ?: also {
        log.info("No messages to clear on index dead letter queue")
      }
  }

  fun clearAllDlqMessagesForEvent() {
    getEventDlqMessageCount()
      .takeIf { it > 0 }
      ?.also { total ->
        eventAwsSqsDlqClient.purgeQueue(PurgeQueueRequest(eventDlqUrl))
        telemetryClient.trackEvent(
          TelemetryEvents.PURGED_EVENT_DLQ.name,
          mapOf("messages-on-queue" to total.toString()),
          null
        )
        log.info("Clear messages on event dead letter queue - found $total message(s)")
      }
      ?: also {
        log.info("No messages to clear on event dead letter queue")
      }
  }

  fun transferEventMessages() {
    getEventDlqMessageCount()
      .takeIf { it > 0 }
      ?.also { total ->
        repeat(total) {
          eventAwsSqsDlqClient.receiveMessage(ReceiveMessageRequest(eventDlqUrl).withMaxNumberOfMessages(1)).messages
            .forEach { msg ->
              eventAwsSqsClient.sendMessage(eventQueueUrl, msg.body)
              eventAwsSqsDlqClient.deleteMessage(DeleteMessageRequest(eventDlqUrl, msg.receiptHandle))
              log.info("Transferred message with from Event DLQ: $msg")
            }
        }
        telemetryClient.trackEvent(
          TelemetryEvents.TRANSFERRED_EVENT_DLQ.name,
          mapOf("messages-on-queue" to total.toString()),
          null
        )
      }
  }

  private fun getEventDlqMessageCount() =
    eventAwsSqsDlqClient.getQueueAttributes(eventDlqUrl, listOf("ApproximateNumberOfMessages"))
      .attributes["ApproximateNumberOfMessages"]
      ?.toInt() ?: 0

  fun transferIndexMessages() {
    indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ()
      .takeIf { it > 0 }
      ?.also { total ->
        repeat(total) {
          indexAwsSqsDlqClient.receiveMessage(ReceiveMessageRequest(indexDlqUrl).withMaxNumberOfMessages(1)).messages
            .forEach { msg ->
              indexAwsSqsClient.sendMessage(indexQueueUrl, msg.body)
              indexAwsSqsDlqClient.deleteMessage(DeleteMessageRequest(indexDlqUrl, msg.receiptHandle))
              log.info("Transferred message with from Index DLQ: $msg")
            }
        }
        telemetryClient.trackEvent(
          TelemetryEvents.TRANSFERRED_INDEX_DLQ.name,
          mapOf("messages-on-queue" to total.toString()),
          null
        )
        log.info("Transfer all Index DLQ messages to main queue - found $total message(s)")
      }
  }
}
