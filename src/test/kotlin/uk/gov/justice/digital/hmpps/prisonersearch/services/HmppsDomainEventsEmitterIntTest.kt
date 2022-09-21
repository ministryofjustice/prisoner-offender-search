package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.readResourceAsText
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PropertyType.IDENTIFIERS
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PropertyType.LOCATION
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest

class HmppsDomainEventsEmitterIntTest : QueueIntegrationTest() {

  @BeforeEach
  fun purgeHmppsEventsQueue() {
    with(hmppsEventsQueue) {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(queueName, sqsClient, queueUrl))
    }
  }

  @Nested
  inner class EmitPrisonerDifferenceEvent {
    @Test
    fun `sends prisoner differences to the domain topic`() {
      hmppsDomainEventEmitter.emitPrisonerDifferenceEvent("some_offender", "some_booking", mapOf(IDENTIFIERS to listOf(), LOCATION to listOf()))

      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

      val result = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
      val message: MsgBody = objectMapper.readValue(result.body)

      assertThatJson(message.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.updated")
      assertThatJson(message.Message).node("version").isEqualTo(1)
      assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
      assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
      assertThatJson(message.Message).node("additionalInfo.offenderNo").isEqualTo("some_offender")
      assertThatJson(message.Message).node("additionalInfo.bookingNo").isEqualTo("some_booking")
      assertThatJson(message.Message).node("additionalInfo.propertyTypes").isArray.containsExactlyInAnyOrder("IDENTIFIERS", "LOCATION")
    }

    @Test
    fun `e2e - will send prisoner updated event to the domain topic`() {
      prisonerIndexService.delete("A1239DD")
      prisonMockServer.stubFor(
        WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
          .willReturn(
            WireMock.aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(PrisonerBuilder(prisonerNumber = "A1239DD").toOffenderBooking())
          )
      )
      val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")

      // create the prisoner in ES
      search(SearchCriteria("A1239DD", null, null), "/results/empty.json")
      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
      eventQueueSqsClient.sendMessage(eventQueueUrl, message)
      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
      await untilCallTo { prisonRequestCountFor("/api/offenders/A1239DD") } matches { it == 1 }

      // Creating a prisoner does not trigger a prisoner difference (TODO SDI-287 handle new prisoners)
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(0)

      // update the prisoner on ES
      prisonMockServer.stubFor(
        WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
          .willReturn(
            WireMock.aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(PrisonerBuilder(prisonerNumber = "A1239DD", firstName = "NEW_NAME").toOffenderBooking())
          )
      )
      eventQueueSqsClient.sendMessage(eventQueueUrl, message)
      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

      // The update should have triggered a prisoner updated domain event
      val result = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
      val msgBody: MsgBody = objectMapper.readValue(result.body)
      assertThatJson(msgBody.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.updated")
      assertThatJson(msgBody.Message).node("additionalInfo.offenderNo").isEqualTo("A1239DD")
      assertThatJson(msgBody.Message).node("additionalInfo.propertyTypes").isArray.containsExactlyInAnyOrder("PERSONAL_DETAILS")
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class MsgBody(val Message: String)
