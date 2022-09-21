package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sns.AmazonSNS
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
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
  private val hmppsDomainEventEmitter = HmppsDomainEventEmitter(objectMapper, hmppsQueueService, diffProperties, clock)
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
  fun `should swallow exceptions`() {
    whenever(topicSnsClient.publish(any())).thenThrow(RuntimeException::class.java)

    assertDoesNotThrow {
      hmppsDomainEventEmitter.emitPrisonerDifferenceEvent("some_offender", mapOf(DiffCategory.LOCATION to listOf()))
    }
  }
}
