package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.Companion.EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PropertyType
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

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
    bookingNo: String?,
    differences: PrisonerDifferences,
  ) {
    runCatching {
      PrisonerUpdatedEvent(offenderNo, bookingNo, differences.keys.toList().sorted())
        .let { event -> PrisonerUpdatedDomainEvent(event, Instant.now(clock), diffProperties.host) }
        .let { domainEvent ->
          PublishRequest(topicArn, objectMapper.writeValueAsString(domainEvent))
            .addMessageAttributesEntry("eventType", MessageAttributeValue().withDataType("String").withStringValue(EVENT_TYPE))
        }.also { publishRequest ->
          topicSnsClient.publish(publishRequest)
        }
    }
      .onFailure {
        log.error(
          "Failed to send prisoner updated event for offenderNo=$offenderNo, bookingNo=$bookingNo, differences=$differences",
          it
        )
      }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val EVENT_TYPE = "prisoner-offender-search.prisoner.updated"
  }
}

data class PrisonerUpdatedEvent(
  val offenderNo: String,
  val bookingNo: String?,
  val propertyTypes: List<PropertyType>,
)

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
      eventType = EVENT_TYPE,
      version = 1,
      description = "A prisoner record has been updated",
      detailUrl = ServletUriComponentsBuilder.fromUriString(host).path("/prisoner/{offenderNo}").buildAndExpand(additionalInfo.offenderNo).toUri().toString(),
    )
}
