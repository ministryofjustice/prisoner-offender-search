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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
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
    hmppsDomainEventEmitter.emitPrisonerDifferenceEvent("some_offender", mapOf(IDENTIFIERS to listOf(), LOCATION to listOf()))

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    val message: MsgBody = objectMapper.readValue(result.body)

    assertThatJson(message.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.updated")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInfo.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message.Message).node("additionalInfo.categoriesChanged").isArray.containsExactlyInAnyOrder("IDENTIFIERS", "LOCATION")
  }

  @Test
  fun `sends prisoner created events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerCreatedEvent("some_offender")

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    val message: MsgBody = objectMapper.readValue(result.body)

    assertThatJson(message.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.created")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInfo.nomsNumber").isEqualTo("some_offender")
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

    // Should receive a prisoner created domain event
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }
    val createResult = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    val createMsgBody: MsgBody = objectMapper.readValue(createResult.body)
    assertThatJson(createMsgBody.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.created")
    assertThatJson(createMsgBody.Message).node("additionalInfo.nomsNumber").isEqualTo("A1239DD")

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
    val updateResult = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    val updateMsgBody: MsgBody = objectMapper.readValue(updateResult.body)
    assertThatJson(updateMsgBody.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.updated")
    assertThatJson(updateMsgBody.Message).node("additionalInfo.nomsNumber").isEqualTo("A1239DD")
    assertThatJson(updateMsgBody.Message).node("additionalInfo.categoriesChanged").isArray.containsExactlyInAnyOrder("PERSONAL_DETAILS")
  }

  @Test
  fun `e2e - will send single prisoner updated event for 2 identical updates`() {
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

    // Should receive a prisoner created domain event
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }
    val createResult = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    val createMsgBody: MsgBody = objectMapper.readValue(createResult.body)
    assertThatJson(createMsgBody.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.created")
    assertThatJson(createMsgBody.Message).node("additionalInfo.nomsNumber").isEqualTo("A1239DD")

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
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    // expecting 3 attempts at messages - the initial create then 2 updates
    await untilCallTo { calledHandleDifferences(times = 3) } matches { it == true }

    // but there is only 1 message on the domain queue because the last update was ignored
    assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
  }

  fun calledHandleDifferences(times: Int): Boolean {
    kotlin.runCatching {
      verify(prisonerDifferenceService, times(times)).handleDifferences(anyOrNull(), any(), any())
    }
      .onFailure {
        return false
      }
    return true
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

    // Should receive a prisoner created domain event
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }
    val createResult = hmppsEventsQueue.sqsClient.receiveMessage(hmppsEventsQueue.queueUrl).messages.first()
    val createMsgBody: MsgBody = objectMapper.readValue(createResult.body)
    assertThatJson(createMsgBody.Message).node("eventType").isEqualTo("prisoner-offender-search.prisoner.created")
    assertThatJson(createMsgBody.Message).node("additionalInfo.nomsNumber").isEqualTo("A1239DD")

    // remember the prisoner event hash
    val insertedPrisonerEventHash = prisonerEventHashRepository.findById("A1239DD").toNullable()?.prisonerHash
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
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { attemptedToSendBothDomainEvents() } matches { it == true }

    // The prisoner hash update should have been rolled back
    val prisonerEventHashAfterAttemptedUpdate = prisonerEventHashRepository.findById("A1239DD").toNullable()?.prisonerHash
    assertThat(prisonerEventHashAfterAttemptedUpdate).isEqualTo(insertedPrisonerEventHash)
  }

  private fun attemptedToSendBothDomainEvents(): Boolean {
    kotlin.runCatching {
      verify(hmppsEventTopicSnsClient, times(2)).publish(any())
    }
      .onFailure {
        return false
      }
    return true
  }
}

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class MsgBody(val Message: String)
