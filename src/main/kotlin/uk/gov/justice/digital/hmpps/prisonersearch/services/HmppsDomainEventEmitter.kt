package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.CREATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.PRISONER_RECEIVED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.PRISONER_RELEASED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.UPDATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReleaseReason
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerDifferences
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

@Suppress("unused")
@Service
class HmppsDomainEventEmitter(
  private val objectMapper: ObjectMapper,
  private val hmppsQueueService: HmppsQueueService,
  private val diffProperties: DiffProperties,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
) {

  private val hmppsDomainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic not found")
  }
  private val topicArn by lazy { hmppsDomainTopic.arn }
  private val topicSnsClient by lazy { hmppsDomainTopic.snsClient }

  fun <T : PrisonerAdditionalInfo> defaultFailureHandler(event: PrisonerDomainEvent<T>, exception: Throwable) {
    log.error(
      "Failed to send event ${event.eventType} for offenderNo= ${event.additionalInfo.nomsNumber}. Event must be manually created",
      exception
    )
    telemetryClient.trackEvent("POSPrisonerDomainEventSendFailure", event.asMap(), null)
  }

  fun <T : PrisonerAdditionalInfo> PrisonerDomainEvent<T>.publish(
    onFailure: (error: Throwable) -> Unit = {
      defaultFailureHandler(this, it)
    }
  ) {
    val request = PublishRequest(topicArn, objectMapper.writeValueAsString(this))
      .addMessageAttributesEntry(
        "eventType",
        MessageAttributeValue().withDataType("String").withStringValue(this.eventType)
      )

    runCatching {
      topicSnsClient.publish(request)
      telemetryClient.trackEvent(this.eventType, this.asMap(), null)
    }.onFailure(onFailure)
  }

  fun emitPrisonerDifferenceEvent(
    offenderNo: String,
    differences: PrisonerDifferences,
  ) {
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
    PrisonerCreatedDomainEvent(PrisonerCreatedEvent(offenderNo), Instant.now(clock), diffProperties.host).publish {
      log.error("Failed to send event $CREATED_EVENT_TYPE for offenderNo=$offenderNo. Event will be retried")
      throw it
    }
  }

  fun emitPrisonerReceiveEvent(
    offenderNo: String,
    reason: PrisonerReceiveReason,
    prisonId: String,
  ) {
    PrisonerReceivedDomainEvent(
      PrisonerReceivedEvent(offenderNo, reason, prisonId),
      Instant.now(clock),
      diffProperties.host
    ).publish()
  }

  enum class PrisonerReceiveReason(val description: String) {
    NEW_ADMISSION("admission on new charges"),
    READMISSION("re-admission on an existing booking"),
    TRANSFERRED("transfer from another prison"),
    RETURN_FROM_COURT("returned back to prison from court"),
    TEMPORARY_ABSENCE_RETURN("returned after a temporary absence"),
  }

  enum class PrisonerReleaseReason(val description: String) {
    TEMPORARY_ABSENCE_RELEASE("released on temporary absence"),
    RELEASED_TO_HOSPITAL("released to a secure hospital"),
    RELEASED("released from prison"),
    SENT_TO_COURT("sent to court"),
    TRANSFERRED("transfer to another prison"),
  }

  fun emitPrisonerReleaseEvent(
    offenderNo: String,
    reason: PrisonerReleaseReason,
    prisonId: String,
  ) {
    PrisonerReleasedDomainEvent(
      PrisonerReleasedEvent(offenderNo, reason, prisonId),
      Instant.now(clock),
      diffProperties.host
    ).publish()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.updated"
    const val CREATED_EVENT_TYPE = "prisoner-offender-search.prisoner.created"
    const val PRISONER_RECEIVED_EVENT_TYPE = "prisoner-offender-search.prisoner.received"
    const val PRISONER_RELEASED_EVENT_TYPE = "prisoner-offender-search.prisoner.released"
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
  val reason: PrisonerReceiveReason,
  val prisonId: String,
) : PrisonerAdditionalInfo()

class PrisonerReceivedDomainEvent(additionalInfo: PrisonerReceivedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerReceivedEvent>(
    additionalInfo = additionalInfo,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner has been received into a prison with reason: ${additionalInfo.reason.description}",
    eventType = PRISONER_RECEIVED_EVENT_TYPE
  )

data class PrisonerReleasedEvent(
  override val nomsNumber: String,
  val reason: PrisonerReleaseReason,
  val prisonId: String,
) : PrisonerAdditionalInfo()

class PrisonerReleasedDomainEvent(additionalInfo: PrisonerReleasedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerReleasedEvent>(
    additionalInfo = additionalInfo,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner has been released from a prison with reason: ${additionalInfo.reason.description}",
    eventType = PRISONER_RELEASED_EVENT_TYPE
  )

fun <T : PrisonerAdditionalInfo> PrisonerDomainEvent<T>.asMap(): Map<String, String> {
  return mutableMapOf(
    "occurredAt" to occurredAt,
    "eventType" to eventType,
    "version" to version.toString(),
    "description" to description,
    "detailUrl" to detailUrl
  ).also { it.putAll(additionalInfo.asMap()) }
}

fun <T : PrisonerAdditionalInfo> T.asMap(): Map<String, String> {
  @Suppress("UNCHECKED_CAST")
  return (this::class as KClass<T>).memberProperties
    .filter { it.get(this) != null }
    .associate { prop ->
      "additionalInfo.${prop.name}" to prop.get(this).toString()
    }
}
