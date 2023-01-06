package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service

@Service
class PrisonerEventListener(
  private val prisonerSyncService: PrisonerSyncService,
  @Qualifier("gson") private val gson: Gson,
  private val telemetryClient: TelemetryClient
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "eventqueue", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun processOffenderEvent(requestJson: String?) {
    try {
      val (message, messageId, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
      val eventType = messageAttributes.eventType.Value
      log.debug("Received message {} type {}", messageId, eventType)

      when (eventType) {
        "EXTERNAL_MOVEMENT_RECORD-INSERTED", "EXTERNAL_MOVEMENT-CHANGED" -> prisonerSyncService.externalMovement(fromJson(message))
        "OFFENDER_BOOKING-CHANGED", "OFFENDER_BOOKING-REASSIGNED", "IMPRISONMENT_STATUS-CHANGED", "BED_ASSIGNMENT_HISTORY-INSERTED", "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED", "ASSESSMENT-CHANGED", "OFFENDER_PROFILE_DETAILS-INSERTED", "OFFENDER_PROFILE_DETAILS-UPDATED", "SENTENCING-CHANGED" -> prisonerSyncService.offenderBookingChange(fromJson(message))
        "BOOKING_NUMBER-CHANGED" -> prisonerSyncService.offenderBookNumberChange(fromJson(message))
        "OFFENDER-INSERTED", "OFFENDER-UPDATED", "OFFENDER_DETAILS-CHANGED", "OFFENDER_ALIAS-CHANGED" -> prisonerSyncService.offenderChange(fromJson(message))
        "ALERT-INSERTED", "ALERT-UPDATED" -> prisonerSyncService.offenderBookingChange(fromJson(message))
        "DATA_COMPLIANCE_DELETE-OFFENDER" -> prisonerSyncService.deleteOffender(fromJson(message))
        "OFFENDER-DELETED" -> prisonerSyncService.maybeDeleteOffender(fromJson(message))

        else -> log.warn("We received a message of event type {} which I really wasn't expecting", eventType)
      }
      log.trace("Finished event message request {}", message)
    } catch (e: Exception) {
      log.error("processOffenderEvent() Unexpected error", e)
      telemetryClient.trackEvent(
        "POSProcessEventRequestError",
        mapOf("requestPayload" to requestJson, "message" to e.message),
        null
      )

      throw e
    }
  }

  private inline fun <reified T> fromJson(message: String): T {
    return gson.fromJson(message, T::class.java)
  }
}

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)

data class ExternalPrisonerMovementMessage(
  val bookingId: Long,
  val movementSeq: Long,
  val offenderIdDisplay: String,
  val fromAgencyLocationId: String,
  val toAgencyLocationId: String,
  val directionCode: String,
  val movementType: String
)

data class OffenderBookingChangedMessage(val bookingId: Long)

data class OffenderChangedMessage(
  val eventType: String,
  val offenderId: Long,
  val offenderIdDisplay: String?
)
