package uk.gov.justice.digital.hmpps.prisonersearch

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test


class MessageIntegrationTest : QueueIntegrationTest() {

  @Test
  fun `will consume a new prisoner booking update`() {
    val message = "/messages/offenderDetailsChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { prisonRequestCountFor("/api/bookings/offenderNo/A7089EY") } matches { it == 1 }

  }
}

private fun String.readResourceAsText(): String {
  return MessageIntegrationTest::class.java.getResource(this).readText()
}