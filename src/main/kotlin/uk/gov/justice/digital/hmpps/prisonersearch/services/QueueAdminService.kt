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
    eventAwsSqsDlqClient.getMessageCount(eventDlqUrl)
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
    eventAwsSqsDlqClient.getMessageCount(eventDlqUrl)
      .takeIf { it > 0 }
      ?.run {
        repeat(this) {
          eventAwsSqsDlqClient.receiveFirstMessageOrNull(eventDlqUrl)?.run {
            eventAwsSqsClient.sendMessage(eventQueueUrl, this.body)
            eventAwsSqsDlqClient.deleteMessage(DeleteMessageRequest(eventDlqUrl, this.receiptHandle))
            log.info("Transferred message from Event DLQ: $this")
          }
            ?: log.info("Expected to transfer message from Event DLQ, but no message was received")
        }
        telemetryClient.trackEvent(
          TelemetryEvents.TRANSFERRED_EVENT_DLQ.name,
          mapOf("messages-on-queue" to this.toString()),
          null
        )
      }
  }

  fun transferIndexMessages() {
    indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ()
      .takeIf { it > 0 }
      ?.run {
        repeat(this) {
          indexAwsSqsDlqClient.receiveFirstMessageOrNull(indexDlqUrl)?.run {
            indexAwsSqsClient.sendMessage(indexQueueUrl, this.body)
            indexAwsSqsDlqClient.deleteMessage(DeleteMessageRequest(indexDlqUrl, this.receiptHandle))
            log.info("Transferred message from Index DLQ: $this")
          }
            ?: log.info("Expected message to transfer from Index DLQ, but no message was received")
        }
        telemetryClient.trackEvent(
          TelemetryEvents.TRANSFERRED_INDEX_DLQ.name,
          mapOf("messages-on-queue" to this.toString()),
          null
        )
        log.info("Transfer all Index DLQ messages to main queue - found $this message(s)")
      }
  }
}

private fun AmazonSQS.getMessageCount(queueUrl: String) = this.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
  .attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0

private fun AmazonSQS.receiveFirstMessageOrNull(queueUrl: String) = this.receiveMessage(ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1)).messages.firstOrNull()
