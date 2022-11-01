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

  fun <T : PrisonerAdditionalInfo> PrisonerDomainEvent<T>.publish(onFailure: (error: Throwable) -> Unit = { log.error("Failed to send event ${this.eventType} for offenderNo= ${this.additionalInfo.nomsNumber}. Event must be manually created") }) {
    val request = PublishRequest(topicArn, objectMapper.writeValueAsString(this))
      .addMessageAttributesEntry(
        "eventType",
        MessageAttributeValue().withDataType("String").withStringValue(this.eventType)
      )

    runCatching {
      log.debug("Publishing $request")
      topicSnsClient.publish(request)
      log.debug("Yep published")
    }.onFailure(onFailure)
  }

  fun emitPrisonerDifferenceEvent(
    offenderNo: String,
    differences: PrisonerDifferences,
  ) {
    log.debug("emitPrisonerDifferenceEvent")
    PrisonerUpdatedDomainEvent(
      PrisonerUpdatedEvent(offenderNo, differences.keys.toList().sorted()),
      Instant.now(clock),
      diffProperties.host
    ).publish {
      log.error("Failed to send event $UPDATED_EVENT_TYPE for offenderNo=$offenderNo, differences=$differences. Event will be retried")
      throw it
    }
  }

  fun emitPrisonerCreatedEvent(offenderNo: String) {
    log.debug("emitPrisonerCreatedEvent")
    PrisonerCreatedDomainEvent(PrisonerCreatedEvent(offenderNo), Instant.now(clock), diffProperties.host).publish {
      log.error("Failed to send event $CREATED_EVENT_TYPE for offenderNo=$offenderNo. Event will be retried")
      throw it
    }
  }

  fun emitPrisonerReceiveEvent(
    offenderNo: String,
    reason: PrisonerReceiveReason,
    prisonId: String,
    fromPrisonId: String? = null,
  ) {
    PrisonerReceivedDomainEvent(
      PrisonerReceivedEvent(offenderNo, reason.name, prisonId, fromPrisonId),
      Instant.now(clock),
      diffProperties.host
    ).publish()
  }

  enum class PrisonerReceiveReason {
    NEW_ADMISSION,
    READMISSION,
    TRANSFERRED,
    RETURN_FROM_COURT,
    TEMPORARY_ABSENCE_RETURN,
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

abstract class PrisonerAdditionalInfo {
  abstract val nomsNumber: String
}

@Suppress("MemberVisibilityCanBePrivate")
open class PrisonerDomainEvent<T : PrisonerAdditionalInfo>(
  val additionalInfo: T,
  val occurredAt: String,
  val eventType: String,
  val version: Int,
  val description: String,
  val detailUrl: String,
) {
  constructor(
    additionalInfo: T,
    occurredAt: Instant = Instant.now(),
    host: String,
    description: String,
    eventType: String
  ) :
    this(
      additionalInfo = additionalInfo,
      occurredAt = ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(occurredAt),
      eventType = eventType,
      version = 1,
      description = description,
      detailUrl = ServletUriComponentsBuilder.fromUriString(host).path("/prisoner/{offenderNo}")
        .buildAndExpand(additionalInfo.nomsNumber).toUri().toString(),
    )
}

data class PrisonerUpdatedEvent(
  override val nomsNumber: String,
  val categoriesChanged: List<DiffCategory>,
) : PrisonerAdditionalInfo()

class PrisonerUpdatedDomainEvent(additionalInfo: PrisonerUpdatedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerUpdatedEvent>(
    additionalInfo = additionalInfo,
    host = host,
    occurredAt = occurredAt,
    description = "A prisoner record has been updated",
    eventType = UPDATED_EVENT_TYPE
  )

data class PrisonerCreatedEvent(override val nomsNumber: String) : PrisonerAdditionalInfo()
class PrisonerCreatedDomainEvent(additionalInfo: PrisonerCreatedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerCreatedEvent>(
    additionalInfo = additionalInfo,
    host = host,
    occurredAt = occurredAt,
    description = "A prisoner record has been created",
    eventType = CREATED_EVENT_TYPE
  )

data class PrisonerReceivedEvent(
  override val nomsNumber: String,
  val reason: String,
  val prisonId: String,
  val fromPrisonId: String?
) : PrisonerAdditionalInfo()

class PrisonerReceivedDomainEvent(additionalInfo: PrisonerReceivedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerReceivedEvent>(
    additionalInfo = additionalInfo,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner has been received into a prison",
    eventType = PRISONER_RECEIVED_EVENT_TYPE
  )
