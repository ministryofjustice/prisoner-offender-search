package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrisonerSyncService(
  private val nomisService: NomisService,
  private val prisonerIndexService: PrisonerIndexService,
  private val telemetryClient: TelemetryClient
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun externalMovement(message: ExternalPrisonerMovementMessage) {
    syncWithBookingId(message.bookingId)
  }

  fun offenderBookingChange(message: OffenderBookingChangedMessage) {
    syncWithBookingId(message.bookingId)
  }

  fun offenderBookNumberChange(message: OffenderBookingChangedMessage) {
    val bookingId = message.bookingId
    log.debug("Check for merged booking for ID {}", bookingId)

    // check for merges
    nomisService.getMergedIdentifiersByBookingId(bookingId)?.forEach {
      prisonerIndexService.delete(it.value)
    }

    syncWithBookingId(bookingId)
  }

  fun offenderChange(message: OffenderChangedMessage) {
    if (message.offenderIdDisplay != null) {
      syncByNomsNumber(message.offenderIdDisplay)
    } else {
      customEventForMissingOffenderIdDisplay(message)
    }
  }

  private fun syncWithBookingId(bookingId: Long) {
    nomisService.getNomsNumberForBooking(bookingId)?.run {
      syncByNomsNumber(this)
    }
  }

  private fun syncByNomsNumber(offenderIdDisplay: String) {
    nomisService.getOffender(offenderIdDisplay)?.run {
      prisonerIndexService.sync(this)
    }
  }

  fun deleteOffender(message: OffenderChangedMessage) {
    if (message.offenderIdDisplay != null) {
      prisonerIndexService.delete(message.offenderIdDisplay)
    } else {
      customEventForMissingOffenderIdDisplay(message)
    }
  }

  private fun customEventForMissingOffenderIdDisplay(
    message: OffenderChangedMessage
  ) {
    val propertiesMap = mapOf(
      "eventType" to message.eventType,
      "offenderId" to message.offenderId.toString()
    )

    telemetryClient.trackEvent("POSMissingOffenderDisplayId", propertiesMap, null)
  }
}
