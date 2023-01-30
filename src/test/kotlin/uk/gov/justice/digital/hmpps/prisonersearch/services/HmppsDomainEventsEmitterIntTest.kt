package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.PrisonMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.readResourceAsText
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.DiffCategory.IDENTIFIERS
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.DiffCategory.LOCATION
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest

class HmppsDomainEventsEmitterIntTest : QueueIntegrationTest() {

  @BeforeEach
  fun purgeHmppsEventsQueue() {
    with(hmppsEventsQueue) {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(queueName, sqsClient, queueUrl))
    }
  }

  @Test
  fun `sends prisoner differences to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerDifferenceEvent(
      "some_offender",
      mapOf(IDENTIFIERS to listOf(), LOCATION to listOf())
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val message = readNextDomainEventMessage()

    assertThatJson(message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.updated")
    assertThatJson(message).node("version").isEqualTo(1)
    assertThatJson(message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message).node("additionalInformation.categoriesChanged").isArray.containsExactlyInAnyOrder(
      "IDENTIFIERS",
      "LOCATION"
    )
  }

  @Test
  fun `sends prisoner created events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerCreatedEvent("some_offender")

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    hmppsEventsQueue.sqsClient.deleteMessage(hmppsEventsQueue.queueUrl, result.receiptHandle)

    val message: MsgBody = objectMapper.readValue(result.body)

    assertThatJson(message.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.created")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
  }

  @Test
  fun `sends prisoner received events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerReceiveEvent(
      "some_offender",
      HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED,
      "MDI"
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    hmppsEventsQueue.sqsClient.deleteMessage(hmppsEventsQueue.queueUrl, result.receiptHandle)
    val message: MsgBody = objectMapper.readValue(result.body)

    assertThatJson(message.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.received")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("description")
      .isEqualTo("A prisoner has been received into a prison with reason: transfer from another prison")
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message.Message).node("additionalInformation.prisonId").isEqualTo("MDI")
    assertThatJson(message.Message).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
  }

  @Test
  fun `sends prisoner released events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerReleaseEvent(
      "some_offender",
      HmppsDomainEventEmitter.PrisonerReleaseReason.TRANSFERRED,
      "MDI"
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    hmppsEventsQueue.sqsClient.deleteMessage(hmppsEventsQueue.queueUrl, result.receiptHandle)
    val message: MsgBody = objectMapper.readValue(result.body)

    assertThatJson(message.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.released")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("description")
      .isEqualTo("A prisoner has been released from a prison with reason: transfer to another prison")
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message.Message).node("additionalInformation.prisonId").isEqualTo("MDI")
    assertThatJson(message.Message).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
  }

  @Test
  fun `e2e - will send prisoner updated event to the domain topic`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD"))

    // update the prisoner on ES
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(PrisonerBuilder(prisonerNumber = "A1239DD", firstName = "NEW_NAME").toOffenderBooking())
        )
    )
    val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")

    eventQueueSqsClient.sendMessage(eventQueueUrl, message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val updateMsgBody = readNextDomainEventMessage()
    assertThatJson(updateMsgBody).node("eventType").isEqualTo("prisoner-offender-search.prisoner.updated")
    assertThatJson(updateMsgBody).node("additionalInformation.nomsNumber").isEqualTo("A1239DD")
    assertThatJson(updateMsgBody).node("additionalInformation.categoriesChanged").isArray.containsExactlyInAnyOrder("PERSONAL_DETAILS")
  }

  @Test
  fun `e2e - will send prisoner release event to the domain topic`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD"))

    // update the prisoner on ES
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(PrisonerBuilder(prisonerNumber = "A1239DD", released = true).toOffenderBooking())
        )
    )
    eventQueueSqsClient.sendMessage(
      eventQueueUrl,
      "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")
    )
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.updated")

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.released")
  }

  @Test
  fun `e2e - will send prisoner received event to the domain topic`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", released = true))

    // update the prisoner on ES
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(PrisonerBuilder(prisonerNumber = "A1239DD", released = false).toOffenderBooking())
        )
    )
    eventQueueSqsClient.sendMessage(
      eventQueueUrl,
      "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")
    )
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.updated")

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.received")
  }

  @Test
  fun `e2e - will send prisoner alerts change event to the domain topic when an alert is added`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", alertCodes = listOf("X" to "XTACT")))

    // update the prisoner on ES
    prisonMockServer.stubOffenderNoFromBookingId("A1239DD")
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                alertCodes = listOf("X" to "XTACT", "W" to "WO")
              ).toOffenderBooking()
            )
        )
    )
    eventQueueSqsClient.sendMessage(
      eventQueueUrl,
      "/messages/offenderAlertsChanged.json".readResourceAsText()
    )
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.updated")

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.alerts-updated")
  }

  @Test
  fun `e2e - will send prisoner alerts change event to the domain topic when an alert is removed`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", alertCodes = listOf("X" to "XTACT", "W" to "WO")))

    // update the prisoner on ES
    prisonMockServer.stubOffenderNoFromBookingId("A1239DD")
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                alertCodes = listOf("W" to "WO")
              ).toOffenderBooking()
            ) // technically the alert should be end dated but this will work equally well
        )
    )
    eventQueueSqsClient.sendMessage(
      eventQueueUrl,
      "/messages/offenderAlertsChanged.json".readResourceAsText()
    )
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.updated")

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("prisoner-offender-search.prisoner.alerts-updated")
  }

  @Test
  fun `e2e - will send single prisoner updated event for 2 identical updates`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD"))

    val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")

    // update the prisoner on ES - TWICE
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(PrisonerBuilder(prisonerNumber = "A1239DD", firstName = "NEW_NAME").toOffenderBooking())
        )
    )
    eventQueueSqsClient.sendMessage(eventQueueUrl, message)
    eventQueueSqsClient.sendMessage(eventQueueUrl, message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    // expecting  2 updates
    await untilAsserted { verify(prisonerDifferenceService, times(2)).handleDifferences(anyOrNull(), any(), any()) }

    // but there is only 1 message on the domain queue because the last update was ignored
    assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
  }

  /*
   * This is to test what happens if we fail to send a domain event.
   * In real life:
   * 1. We receive a prison event indicating "something" happened to the prisoner
   * 2. The prisoner is updated in Elastic Search
   * 3. We update the prisoner event hash to reflect the changes to the prisoner
   * 4. We try to send a domain event BUT IT FAILS
   * 5. The prison event is rejected and is sent to the DLQ
   * 6. The prison event is automatically retried
   * 7. We attempt to update the prisoner event hash again and if successful then send a domain event
   * 8. If the previous update of the prisoner event hash persisted then we can't update it so a domain event would not be sent
   *
   * So this test checks that the prisoner event hash update is rolled back if sending the domain event fails.
   */
  @Test
  fun `e2e - should not update prisoner hash if there is an exception when sending the event`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD"))

    val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")

    // remember the prisoner event hash
    val insertedPrisonerEventHash = prisonerEventHashRepository.findByIdOrNull("A1239DD")?.prisonerHash
    assertThat(insertedPrisonerEventHash).isNotNull

    // update the prisoner on ES BUT fail to send an event
    doThrow(RuntimeException("Failed to send event")).whenever(hmppsEventTopicSnsClient).publish(any())
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(PrisonerBuilder(prisonerNumber = "A1239DD", firstName = "NEW_NAME").toOffenderBooking())
        )
    )
    eventQueueSqsClient.sendMessage(eventQueueUrl, message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    await untilAsserted { verify(hmppsEventTopicSnsClient).publish(any()) }

    // The prisoner hash update should have been rolled back
    val prisonerEventHashAfterAttemptedUpdate =
      prisonerEventHashRepository.findByIdOrNull("A1239DD")?.prisonerHash
    assertThat(prisonerEventHashAfterAttemptedUpdate).isEqualTo(insertedPrisonerEventHash)
  }

  fun recreatePrisoner(builder: PrisonerBuilder) {
    val prisonerNumber: String = builder.prisonerNumber

    prisonerIndexService.delete(prisonerNumber)
    prisonMockServer.stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/A1239DD"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(builder.toOffenderBooking())
        )
    )
    eventQueueSqsClient.sendMessage(
      eventQueueUrl,
      "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", prisonerNumber)
    )

    // create the prisoner in ES
    await untilCallTo { prisonRequestCountFor("/api/offenders/$prisonerNumber") } matches { it == 1 }

    // delete create events
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it != 0 }

    await untilCallTo { prisonerEventHashRepository.findById(prisonerNumber) } matches { it != null }

    purgeHmppsEventsQueue()

    Mockito.reset(hmppsEventTopicSnsClient)
    Mockito.reset(prisonerDifferenceService)
  }

  fun readNextDomainEventMessage(): String {
    val updateResult = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    hmppsEventsQueue.sqsClient.deleteMessage(hmppsEventsQueue.queueUrl, updateResult.receiptHandle)
    return objectMapper.readValue<MsgBody>(updateResult.body).Message
  }

  private fun PrisonMockServer.stubOffenderNoFromBookingId(prisonerNumber: String) {
    this.stubFor(
      WireMock.get(WireMock.urlPathMatching("/api/bookings/\\d*"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(PrisonerBuilder(prisonerNumber = prisonerNumber).toOffenderBooking())
        )
    )
  }
}

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class MsgBody(val Message: String)
