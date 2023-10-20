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
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.PRISONER_ALERTS_UPDATED_EVENT_TYPE
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
  private val clock: Clock?,
  private val telemetryClient: TelemetryClient,
) {

  private val hmppsDomainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic not found")
  }
  private val topicArn by lazy { hmppsDomainTopic.arn }
  private val topicSnsClient by lazy { hmppsDomainTopic.snsClient }

  fun <T : PrisonerAdditionalInformation> defaultFailureHandler(event: PrisonerDomainEvent<T>, exception: Throwable) {
    log.error(
      "Failed to send event ${event.eventType} for offenderNo= ${event.additionalInformation.nomsNumber}. Event must be manually created",
      exception,
    )
    telemetryClient.trackEvent("POSPrisonerDomainEventSendFailure", event.asMap(), null)
  }

  fun <T : PrisonerAdditionalInformation> PrisonerDomainEvent<T>.publish(
    onFailure: (error: Throwable) -> Unit = {
      defaultFailureHandler(this, it)
    },
  ) {
    val event = PrisonerDomainEvent(
      additionalInformation = this.additionalInformation,
      eventType = "${diffProperties.prefix}${this.eventType}",
      occurredAt = this.occurredAt,
      version = this.version,
      description = this.description,
      detailUrl = this.detailUrl,
    )

    val request = PublishRequest(topicArn, objectMapper.writeValueAsString(event))
      .addMessageAttributesEntry(
        "eventType",
        MessageAttributeValue().withDataType("String").withStringValue(event.eventType),
      )

    runCatching {
      topicSnsClient.publish(request)
      telemetryClient.trackEvent(event.eventType, event.asMap(), null)
    }.onFailure(onFailure)
  }

  fun emitPrisonerDifferenceEvent(
    offenderNo: String,
    differences: PrisonerDifferences,
  ) {
    PrisonerUpdatedDomainEvent(
      PrisonerUpdatedEvent(offenderNo, differences.keys.toList().sorted()),
      Instant.now(clock),
      diffProperties.host,
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
    occurredAt: Instant? = null,
  ) {
    PrisonerReceivedDomainEvent(
      PrisonerReceivedEvent(offenderNo, reason, prisonId),
      occurredAt ?: Instant.now(clock),
      diffProperties.host,
    ).publish()
  }

  enum class PrisonerReceiveReason(val description: String) {
    NEW_ADMISSION("admission on new charges"),
    READMISSION("re-admission on an existing booking"),
    TRANSFERRED("transfer from another prison"),
    RETURN_FROM_COURT("returned back to prison from court"),
    TEMPORARY_ABSENCE_RETURN("returned after a temporary absence"),
    POST_MERGE_ADMISSION("admission following an offender merge"),
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
      diffProperties.host,
    ).publish()
  }

  fun emitPrisonerAlertsUpdatedEvent(
    offenderNo: String,
    bookingId: String?,
    alertsAdded: Set<String>,
    alertsRemoved: Set<String>,
  ) {
    PrisonerAlertsUpdatedDomainEvent(
      PrisonerAlertsUpdatedEvent(offenderNo, bookingId, alertsAdded, alertsRemoved),
      Instant.now(clock),
      diffProperties.host,
    ).publish()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.updated"
    const val CREATED_EVENT_TYPE = "prisoner-offender-search.prisoner.created"
    const val PRISONER_RECEIVED_EVENT_TYPE = "prisoner-offender-search.prisoner.received"
    const val PRISONER_RELEASED_EVENT_TYPE = "prisoner-offender-search.prisoner.released"
    const val PRISONER_ALERTS_UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.alerts-updated"
  }
}

abstract class PrisonerAdditionalInformation {
  abstract val nomsNumber: String
}

@Suppress("MemberVisibilityCanBePrivate")
open class PrisonerDomainEvent<T : PrisonerAdditionalInformation>(
  val additionalInformation: T,
  val occurredAt: String,
  val eventType: String,
  val version: Int,
  val description: String,
  val detailUrl: String,
) {
  constructor(
    additionalInformation: T,
    occurredAt: Instant = Instant.now(),
    host: String,
    description: String,
    eventType: String,
  ) :
    this(
      additionalInformation = additionalInformation,
      occurredAt = ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(occurredAt),
      eventType = eventType,
      version = 1,
      description = description,
      detailUrl = ServletUriComponentsBuilder.fromUriString(host).path("/prisoner/{offenderNo}")
        .buildAndExpand(additionalInformation.nomsNumber).toUri().toString(),
    )
}

data class PrisonerUpdatedEvent(
  override val nomsNumber: String,
  val categoriesChanged: List<DiffCategory>,
) : PrisonerAdditionalInformation()

class PrisonerUpdatedDomainEvent(additionalInformation: PrisonerUpdatedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerUpdatedEvent>(
    additionalInformation = additionalInformation,
    host = host,
    occurredAt = occurredAt,
    description = "A prisoner record has been updated",
    eventType = UPDATED_EVENT_TYPE,
  )

data class PrisonerCreatedEvent(override val nomsNumber: String) : PrisonerAdditionalInformation()
class PrisonerCreatedDomainEvent(additionalInformation: PrisonerCreatedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerCreatedEvent>(
    additionalInformation = additionalInformation,
    host = host,
    occurredAt = occurredAt,
    description = "A prisoner record has been created",
    eventType = CREATED_EVENT_TYPE,
  )

data class PrisonerReceivedEvent(
  override val nomsNumber: String,
  val reason: PrisonerReceiveReason,
  val prisonId: String,
) : PrisonerAdditionalInformation()

class PrisonerReceivedDomainEvent(additionalInformation: PrisonerReceivedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerReceivedEvent>(
    additionalInformation = additionalInformation,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner has been received into a prison with reason: ${additionalInformation.reason.description}",
    eventType = PRISONER_RECEIVED_EVENT_TYPE,
  )

data class PrisonerReleasedEvent(
  override val nomsNumber: String,
  val reason: PrisonerReleaseReason,
  val prisonId: String,
) : PrisonerAdditionalInformation()

class PrisonerReleasedDomainEvent(additionalInformation: PrisonerReleasedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerReleasedEvent>(
    additionalInformation = additionalInformation,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner has been released from a prison with reason: ${additionalInformation.reason.description}",
    eventType = PRISONER_RELEASED_EVENT_TYPE,
  )

data class PrisonerAlertsUpdatedEvent(
  override val nomsNumber: String,
  val bookingId: String?,
  val alertsAdded: Set<String>,
  val alertsRemoved: Set<String>,
) : PrisonerAdditionalInformation()

class PrisonerAlertsUpdatedDomainEvent(additionalInformation: PrisonerAlertsUpdatedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerAlertsUpdatedEvent>(
    additionalInformation = additionalInformation,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner had their alerts updated, added: ${additionalInformation.alertsAdded.size}, removed: ${additionalInformation.alertsRemoved.size}",
    eventType = PRISONER_ALERTS_UPDATED_EVENT_TYPE,
  )

fun <T : PrisonerAdditionalInformation> PrisonerDomainEvent<T>.asMap(): Map<String, String> {
  return mutableMapOf(
    "occurredAt" to occurredAt,
    "eventType" to eventType,
    "version" to version.toString(),
    "description" to description,
    "detailUrl" to detailUrl,
  ).also { it.putAll(additionalInformation.asMap()) }
}

fun <T : PrisonerAdditionalInformation> T.asMap(): Map<String, String> {
  @Suppress("UNCHECKED_CAST")
  return (this::class as KClass<T>).memberProperties
    .filter { it.get(this) != null }
    .associate { prop ->
      "additionalInformation.${prop.name}" to prop.get(this).toString()
    }
}
