package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.CREATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.PRISONER_RECEIVED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.UPDATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerDifferences
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

@Suppress("unused")
@Service
class HmppsDomainEventEmitter(
  private val objectMapper: ObjectMapper,
  private val hmppsQueueService: HmppsQueueService,
  private val diffProperties: DiffProperties,
  private val clock: Clock,
) {

  private val hmppsDomainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic not found")
  }
  private val topicArn by lazy { hmppsDomainTopic.arn }
  private val topicSnsClient by lazy { hmppsDomainTopic.snsClient }

  fun emitPrisonerDifferenceEvent(
    offenderNo: String,
    differences: PrisonerDifferences,
  ) {
    runCatching {
      PrisonerUpdatedDomainEvent(PrisonerUpdatedEvent(offenderNo, differences.keys.toList().sorted()), Instant.now(clock), diffProperties.host)
        .let { domainEvent ->
          PublishRequest(topicArn, objectMapper.writeValueAsString(domainEvent))
            .addMessageAttributesEntry("eventType", MessageAttributeValue().withDataType("String").withStringValue(UPDATED_EVENT_TYPE))
        }.also { publishRequest ->
          topicSnsClient.publish(publishRequest)
        }
    }
      .onFailure {
        log.error("Failed to send event $UPDATED_EVENT_TYPE for offenderNo=$offenderNo, differences=$differences")
        throw it
      }
  }

  fun emitPrisonerCreatedEvent(offenderNo: String) {
    runCatching {
      PrisonerCreatedDomainEvent(PrisonerCreatedEvent(offenderNo), Instant.now(clock), diffProperties.host)
        .let { domainEvent ->
          PublishRequest(topicArn, objectMapper.writeValueAsString(domainEvent))
            .addMessageAttributesEntry("eventType", MessageAttributeValue().withDataType("String").withStringValue(CREATED_EVENT_TYPE))
        }.also { publishRequest ->
          topicSnsClient.publish(publishRequest)
        }
    }
      .onFailure {
        log.error("Failed to send event $CREATED_EVENT_TYPE for offenderNo=$offenderNo", it)
        throw it
      }
  }

  enum class PrisonerReceiveReason {
    NEW_ADMISSION,
    READMISSION,
    TRANSFERRED,
    RETURN_FROM_COURT,
    TEMPORARY_ABSENCE_RETURN,
  }
  fun emitPrisonerReceiveEvent(
    offenderNo: String,
    reason: PrisonerReceiveReason,
    prisonId: String,
    fromPrisonId: String? = null,
  ) {
    runCatching {
      PrisonerReceivedDomainEvent(PrisonerReceivedEvent(offenderNo, reason.name, prisonId, fromPrisonId), Instant.now(clock), diffProperties.host)
        .let { domainEvent ->
          PublishRequest(topicArn, objectMapper.writeValueAsString(domainEvent))
            .addMessageAttributesEntry("eventType", MessageAttributeValue().withDataType("String").withStringValue(PRISONER_RECEIVED_EVENT_TYPE))
        }.also { publishRequest ->
          topicSnsClient.publish(publishRequest)
        }
    }
      .onFailure {
        log.error("Failed to send event $CREATED_EVENT_TYPE for offenderNo=$offenderNo", it)
        // TODO how do we retry without sending other messages?
      }
  }
  enum class PrisonerReleaseReason {
    TEMPORARY_ABSENCE_RELEASE,
    RELEASED_TO_HOSPITAL,
    RELEASED,
    SENT_TO_COURT,
    TRANSFERRED,
  }
  fun emitPrisonerReleaseEvent(
    offenderNo: String,
    reason: PrisonerReleaseReason,
    fromPrisonId: String,
  ) {
    // TODO create event and send to hmpps domain topic
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.updated"
    const val CREATED_EVENT_TYPE = "prisoner-offender-search.prisoner.created"
    const val PRISONER_RECEIVED_EVENT_TYPE = "prisoner-offender-search.prisoner.received"
  }
}

data class PrisonerUpdatedEvent(
  val nomsNumber: String,
  val categoriesChanged: List<DiffCategory>,
)

data class PrisonerCreatedEvent(val nomsNumber: String)

data class PrisonerUpdatedDomainEvent(
  val additionalInfo: PrisonerUpdatedEvent,
  val occurredAt: String,
  val eventType: String,
  val version: Int,
  val description: String,
  val detailUrl: String,
) {
  constructor(additionalInfo: PrisonerUpdatedEvent, occurredAt: Instant, host: String) :
    this(
      additionalInfo = additionalInfo,
      occurredAt = ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(occurredAt),
      eventType = UPDATED_EVENT_TYPE,
      version = 1,
      description = "A prisoner record has been updated",
      detailUrl = ServletUriComponentsBuilder.fromUriString(host).path("/prisoner/{offenderNo}").buildAndExpand(additionalInfo.nomsNumber).toUri().toString(),
    )
}

data class PrisonerCreatedDomainEvent(
  val additionalInfo: PrisonerCreatedEvent,
  val occurredAt: String,
  val eventType: String,
  val version: Int,
  val description: String,
  val detailUrl: String,
) {
  constructor(additionalInfo: PrisonerCreatedEvent, occurredAt: Instant, host: String) :
    this(
      additionalInfo = additionalInfo,
      occurredAt = ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(occurredAt),
      eventType = CREATED_EVENT_TYPE,
      version = 1,
      description = "A prisoner record has been created",
      detailUrl = ServletUriComponentsBuilder.fromUriString(host).path("/prisoner/{offenderNo}").buildAndExpand(additionalInfo.nomsNumber).toUri().toString(),
    )
}

data class PrisonerReceivedEvent(val nomsNumber: String, val reason: String, val prisonId: String, val fromPrisonId: String?)

data class PrisonerReceivedDomainEvent(
  val additionalInfo: PrisonerReceivedEvent,
  val occurredAt: String,
  val eventType: String,
  val version: Int,
  val description: String,
  val detailUrl: String,
) {
  constructor(additionalInfo: PrisonerReceivedEvent, occurredAt: Instant, host: String) :
    this(
      additionalInfo = additionalInfo,
      occurredAt = ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(occurredAt),
      eventType = PRISONER_RECEIVED_EVENT_TYPE,
      version = 1,
      description = "A prisoner has been received into a prison",
      detailUrl = ServletUriComponentsBuilder.fromUriString(host).path("/prisoner/{offenderNo}").buildAndExpand(additionalInfo.nomsNumber).toUri().toString(),
    )
}
