package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PropertyType.IDENTIFIERS
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PropertyType.LOCATION
import uk.gov.justice.hmpps.sqs.MissingQueueException

class HmppsDomainEventsEmitterIntTest : QueueIntegrationTest() {

  @Autowired
  private lateinit var hmppsDomainEventEmitter: HmppsDomainEventEmitter

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  protected val topicQueue by lazy { hmppsQueueService.findByQueueId("hmppseventtestqueue") ?: throw MissingQueueException("hmppseventtestqueue queue not found") }

  fun getNumberOfMessagesCurrentlyOnDomainQueue(): Int? {
    val queueAttributes = topicQueue.sqsClient.getQueueAttributes(topicQueue.queueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  @Nested
  inner class EmitPrisonerDifferenceEvent {
    @Test
    fun `sends prisoner differences to the domain topic`() {
      hmppsDomainEventEmitter.emitPrisonerDifferenceEvent("some_offender", "some_booking", mapOf(IDENTIFIERS to listOf(), LOCATION to listOf()))

      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

      val result = topicQueue.sqsClient.receiveMessage(topicQueue.queueUrl).messages.first()
      val message: MsgBody = objectMapper.readValue(result.body)

      assertThatJson(message.Message).node("eventType").isEqualTo("prisoner-offender-search.offender.updated")
      assertThatJson(message.Message).node("version").isEqualTo(1)
      assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
      assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
      assertThatJson(message.Message).node("additionalInfo.offenderNo").isEqualTo("some_offender")
      assertThatJson(message.Message).node("additionalInfo.bookingNo").isEqualTo("some_booking")
      assertThatJson(message.Message).node("additionalInfo.propertyTypes").isArray.containsExactlyInAnyOrder("IDENTIFIERS", "LOCATION")
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class MsgBody(val Message: String)
