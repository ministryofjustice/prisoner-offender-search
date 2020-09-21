package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.ReceiveMessageResult
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class QueueAdminServiceTest {

  private val indexAwsSqsClient = mock<AmazonSQS>()
  private val indexAwsSqsDlqClient = mock<AmazonSQS>()
  private val eventAwsSqsClient = mock<AmazonSQS>()
  private val eventAwsSqsDlqClient = mock<AmazonSQS>()
  private val indexQueueService = mock<IndexQueueService>()
  private val telemetryClient = mock<TelemetryClient>()
  private lateinit var queueAdminService: QueueAdminService

  @BeforeEach
  internal fun setUp() {
    whenever(indexAwsSqsClient.getQueueUrl("index-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-queue"))
    whenever(indexAwsSqsDlqClient.getQueueUrl("index-dlq")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-dlq"))
    whenever(eventAwsSqsClient.getQueueUrl("event-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:event-queue"))
    whenever(eventAwsSqsDlqClient.getQueueUrl("event-dlq")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:event-dlq"))
    queueAdminService = QueueAdminService(
        indexAwsSqsClient = indexAwsSqsClient,
        indexAwsSqsDlqClient = indexAwsSqsDlqClient,
        indexQueueService = indexQueueService,
        telemetryClient = telemetryClient,
        indexQueueName = "index-queue",
        indexDlqName = "index-dlq",
        gson = Gson()
    )
  }

  @Nested
  inner class ClearAllMessagesForIndex {
    @Test
    internal fun `will purge index queue of messages`() {
      whenever(indexAwsSqsClient.getQueueUrl("index-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-queue"))

      queueAdminService.clearAllIndexQueueMessages()
      verify(indexAwsSqsClient).purgeQueue(check {
        assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:index-queue")
      })
    }

    @Test
    internal fun `will send a telemetry event`() {
      whenever(indexAwsSqsClient.getQueueUrl("index-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-queue"))

      queueAdminService.clearAllIndexQueueMessages()

      verify(telemetryClient).trackEvent("PURGED_INDEX_QUEUE", mapOf("messages-on-queue" to "0"), null)
    }
  }

  @Nested
  inner class ClearAllDlqMessagesForIndex {
    @Test
    internal fun `will purge index dlq of messages`() {
      whenever(indexAwsSqsDlqClient.getQueueUrl("index-dlq")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-dlq"))

      queueAdminService.clearAllDlqMessagesForIndex()
      verify(indexAwsSqsDlqClient).purgeQueue(check {
        assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:index-dlq")
      })
    }
  }

  @Nested
  inner class TransferAllIndexDlqMessages {

    private val indexQueueUrl = "arn:eu-west-1:index-queue"
    private val indexDlqUrl = "arn:eu-west-1:index-dlq"

    @Test
    internal fun `will read single message from index dlq`() {
      stubDlqMessageCount(1)
      whenever(indexAwsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("A1234AA"))))

      queueAdminService.transferIndexMessages()

      verify(indexAwsSqsDlqClient).receiveMessage(check<ReceiveMessageRequest> {
        assertThat(it.queueUrl).isEqualTo(indexDlqUrl)
      })
    }

    @Test
    internal fun `will read multiple messages from dlq`() {
      stubDlqMessageCount(3)
      whenever(indexAwsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("A1234AA"))))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("B1234BB"))))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("C1234CC"))))

      queueAdminService.transferIndexMessages()

      verify(indexAwsSqsDlqClient, times(3)).receiveMessage(check<ReceiveMessageRequest> {
        assertThat(it.queueUrl).isEqualTo(indexDlqUrl)
      })
    }

    @Test
    internal fun `will send single message to the index queue`() {
      stubDlqMessageCount(1)
      whenever(indexAwsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("A1234AA"))))

      queueAdminService.transferIndexMessages()

      verify(indexAwsSqsClient).sendMessage(indexQueueUrl, populateOffenderMessage("A1234AA"))
    }

    @Test
    internal fun `will send multiple messages to the index queue`() {
      stubDlqMessageCount(3)
      whenever(indexAwsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("A1234AA"))))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("B1234BB"))))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("C1234CC"))))

      queueAdminService.transferIndexMessages()

      verify(indexAwsSqsClient).sendMessage(indexQueueUrl, populateOffenderMessage("A1234AA"))
      verify(indexAwsSqsClient).sendMessage(indexQueueUrl, populateOffenderMessage("B1234BB"))
      verify(indexAwsSqsClient).sendMessage(indexQueueUrl, populateOffenderMessage("C1234CC"))
    }

    @Test
    internal fun `will send a telemetry event`() {
      stubDlqMessageCount(1)
      whenever(indexAwsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
          .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(populateOffenderMessage("A1234AA"))))

      queueAdminService.transferIndexMessages()

      verify(telemetryClient).trackEvent("TRANSFERRED_INDEX_DLQ", mapOf("messages-on-queue" to "1"), null)
    }

    private fun stubDlqMessageCount(count: Int) =
        whenever(indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ()).thenReturn(count)
  }
}

fun populateOffenderMessage(offenderNumber: String) = """
  {
    "requestType": "OFFENDER",
    "prisonerNumber":"$offenderNumber"
  }
  """.trimIndent()
