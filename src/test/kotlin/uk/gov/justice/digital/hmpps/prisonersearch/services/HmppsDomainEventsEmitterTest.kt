package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.DiffCategory
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
 * Most test scenarios are covered by the integration tests in HmppsDomainEventsEmitterIntTest
 */
class HmppsDomainEventsEmitterTest {

  private val objectMapper = ObjectMapper()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val diffProperties = mock<DiffProperties>()
  private val clock = mock<Clock>()
  private val telemetryClient = mock<TelemetryClient>()
  private val hmppsDomainEventEmitter =
    HmppsDomainEventEmitter(objectMapper, hmppsQueueService, diffProperties, clock, telemetryClient)
  private val topicSnsClient = mock<AmazonSNS>()
  private val hmppsEventsTopic = HmppsTopic("hmppseventstopic", "some_arn", topicSnsClient)

  @BeforeEach
  fun `setup mocks`() {
    whenever(hmppsQueueService.findByTopicId(anyString())).thenReturn(hmppsEventsTopic)
    Clock.fixed(Instant.parse("2022-09-16T10:40:34Z"), ZoneId.of("UTC")).also {
      whenever(clock.instant()).thenReturn(it.instant())
      whenever(clock.zone).thenReturn(it.zone)
    }
    whenever(diffProperties.host).thenReturn("some_host")
  }

  @Nested
  inner class PrisonerDifferenceEvent {
    @Test
    fun `should include event type as a message attribute`() {
      hmppsDomainEventEmitter.emitPrisonerDifferenceEvent("some_offender", mapOf(DiffCategory.LOCATION to listOf()))

      verify(topicSnsClient).publish(
        check {
          assertThat(it.messageAttributes["eventType"]?.stringValue).isEqualTo("prisoner-offender-search.prisoner.updated")
        }
      )
    }

    @Test
    fun `should not swallow exceptions`() {
      whenever(topicSnsClient.publish(any())).thenThrow(RuntimeException::class.java)

      assertThatThrownBy {
        hmppsDomainEventEmitter.emitPrisonerDifferenceEvent("some_offender", mapOf(DiffCategory.LOCATION to listOf()))
      }.isInstanceOf(RuntimeException::class.java)
    }
  }

  @Nested
  inner class PrisonerCreatedEvent {
    @Test
    fun `should include event type as a message attribute`() {
      hmppsDomainEventEmitter.emitPrisonerCreatedEvent("some_offender")

      verify(topicSnsClient).publish(
        check {
          assertThat(it.messageAttributes["eventType"]?.stringValue).isEqualTo("prisoner-offender-search.prisoner.created")
        }
      )
    }

    @Test
    fun `should not swallow exceptions`() {
      whenever(topicSnsClient.publish(any())).thenThrow(RuntimeException::class.java)

      assertThatThrownBy {
        hmppsDomainEventEmitter.emitPrisonerCreatedEvent("some_offender")
      }.isInstanceOf(RuntimeException::class.java)
    }
  }

  @Nested
  inner class PrisonerReceivedEvent {
    @Test
    fun `should include event type as a message attribute`() {
      hmppsDomainEventEmitter.emitPrisonerReceiveEvent("some_offender", READMISSION, "MDI")

      verify(topicSnsClient).publish(
        check {
          assertThat(it.messageAttributes["eventType"]?.stringValue).isEqualTo("prisoner-offender-search.prisoner.received")
        }
      )
    }

    @Test
    fun `should also log event`() {
      hmppsDomainEventEmitter.emitPrisonerReceiveEvent("some_offender", READMISSION, "MDI")

      verify(telemetryClient).trackEvent(
        eq("prisoner-offender-search.prisoner.received"),
        check {
          assertThat(it["eventType"]).isEqualTo("prisoner-offender-search.prisoner.received")
          assertThat(it["version"]).isEqualTo("1")
          assertThat(it["description"]).isEqualTo("A prisoner has been received into a prison with reason: re-admission on an existing booking")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.reason"]).isEqualTo("READMISSION")
          assertThat(it["additionalInformation.prisonId"]).isEqualTo("MDI")
        },
        isNull()
      )
    }

    @Test
    fun `should swallow exceptions and indicate a manual fix is required`() {
      whenever(topicSnsClient.publish(any())).thenThrow(RuntimeException::class.java)

      hmppsDomainEventEmitter.emitPrisonerReceiveEvent("some_offender", READMISSION, "MDI")
      verify(telemetryClient).trackEvent(
        eq("POSPrisonerDomainEventSendFailure"),
        check {
          assertThat(it["eventType"]).isEqualTo("prisoner-offender-search.prisoner.received")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.reason"]).isEqualTo("READMISSION")
          assertThat(it["additionalInformation.prisonId"]).isEqualTo("MDI")
        },
        isNull()
      )
    }
  }
  @Nested
  inner class PrisonerAlertsUpdatedEvent {
    @Test
    fun `should include event type as a message attribute`() {
      hmppsDomainEventEmitter.emitPrisonerAlertsUpdatedEvent("some_offender", setOf("XA"), setOf())

      verify(topicSnsClient).publish(
        check {
          assertThat(it.messageAttributes["eventType"]?.stringValue).isEqualTo("prisoner-offender-search.prisoner.alerts-updated")
        }
      )
    }

    @Test
    fun `should also log event`() {
      hmppsDomainEventEmitter.emitPrisonerAlertsUpdatedEvent("some_offender", setOf("XA", "XT"), setOf("ZZ"))

      verify(telemetryClient).trackEvent(
        eq("prisoner-offender-search.prisoner.alerts-updated"),
        check {
          assertThat(it["eventType"]).isEqualTo("prisoner-offender-search.prisoner.alerts-updated")
          assertThat(it["version"]).isEqualTo("1")
          assertThat(it["description"]).isEqualTo("A prisoner had their alerts updated, added: 2, removed: 1")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.alertsAdded"]).isEqualTo("[XA, XT]")
          assertThat(it["additionalInformation.alertsRemoved"]).isEqualTo("[ZZ]")
        },
        isNull()
      )
    }

    @Test
    fun `should swallow exceptions and indicate a manual fix is required`() {
      whenever(topicSnsClient.publish(any())).thenThrow(RuntimeException::class.java)

      hmppsDomainEventEmitter.emitPrisonerAlertsUpdatedEvent("some_offender", setOf("XA"), setOf())

      verify(telemetryClient).trackEvent(
        eq("POSPrisonerDomainEventSendFailure"),
        check {
          assertThat(it["eventType"]).isEqualTo("prisoner-offender-search.prisoner.alerts-updated")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.alertsAdded"]).isEqualTo("[XA]")
          assertThat(it["additionalInformation.alertsRemoved"]).isEqualTo("[]")
        },
        isNull()
      )
    }
  }
}
