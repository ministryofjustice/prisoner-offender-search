package uk.gov.justice.digital.hmpps.prisonersearch.resource

import com.fasterxml.jackson.module.kotlin.readValue
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.MsgBody
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest

class DomainEventsResourceTest : QueueIntegrationTest() {

  @BeforeEach
  fun purgeHmppsEventsQueue() {
    with(hmppsEventsQueue) {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(queueName, sqsClient, queueUrl))
    }
  }

  @Test
  fun `access forbidden when no authority`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation())
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `reason code must be valid`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "BANANAS",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `occurredAt must be present`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `prisonId must be present`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `sends prisoner receive event to the domain topic`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isAccepted

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val message = readNextDomainEventMessage()

    assertThatJson(message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.received")
    assertThatJson(message).node("version").isEqualTo(1)
    assertThatJson(message).node("occurredAt").isEqualTo("2020-07-19T12:30:12+01:00")
    assertThatJson(message).node("additionalInformation.nomsNumber").isEqualTo("A2483AA")
    assertThatJson(message).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
  }

  fun readNextDomainEventMessage(): String {
    val updateResult = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    hmppsEventsQueue.sqsClient.deleteMessage(hmppsEventsQueue.queueUrl, updateResult.receiptHandle)
    return objectMapper.readValue<MsgBody>(updateResult.body).Message
  }
}
