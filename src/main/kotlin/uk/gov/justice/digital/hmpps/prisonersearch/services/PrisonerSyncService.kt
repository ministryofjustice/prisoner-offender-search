package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictivePatient

@Service
class PrisonerSyncService(
  private val nomisService: NomisService,
  private val restrictedPatientService: RestrictedPatientService,
  private val prisonerIndexService: PrisonerIndexService,
  private val telemetryClient: TelemetryClient
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun externalMovement(message: ExternalPrisonerMovementMessage) {
    nomisService.getOffender(message.bookingId)?.let {
      prisonerIndexService.sync(withRestrictedPatientIfOut(it))
    }
  }

  fun offenderBookingChange(message: OffenderBookingChangedMessage) {
    nomisService.getOffender(message.bookingId)?.let {
      prisonerIndexService.sync(withRestrictedPatientIfOut(it))
    }
  }

  fun offenderBookNumberChange(message: OffenderBookingChangedMessage) {
    val bookingId = message.bookingId
    log.debug("Check for merged booking for ID {}", bookingId)

    // check for merges
    nomisService.getMergedIdentifiersByBookingId(bookingId)?.forEach {
      prisonerIndexService.delete(it.value)
    }

    nomisService.getOffender(bookingId)?.let {
      prisonerIndexService.sync(withRestrictedPatientIfOut(it))
    }
  }

  fun offenderChange(message: OffenderChangedMessage) {
    if (message.offenderIdDisplay != null) {
      nomisService.getOffender(message.offenderIdDisplay)?.let {
        prisonerIndexService.sync(withRestrictedPatientIfOut(it))
      }
    } else {
      customEventForMissingOffenderIdDisplay(message)
    }
  }

  fun deleteOffender(message: OffenderChangedMessage) {
    if (message.offenderIdDisplay != null) {
      prisonerIndexService.delete(message.offenderIdDisplay)
    } else {
      customEventForMissingOffenderIdDisplay(message)
    }
  }

  fun withRestrictedPatientIfOut(booking: OffenderBooking): OffenderBooking {
    if (booking.assignedLivingUnit?.agencyId != "OUT") return booking
    val restrictivePatient = restrictedPatientService.getRestrictedPatient(booking.offenderNo) ?: return booking

    return booking.copy(
      restrictivePatient = RestrictivePatient(
        supportingPrison = restrictivePatient.supportingPrison,
        dischargedHospital = restrictivePatient.hospitalLocation,
        dischargeDate = restrictivePatient.dischargeTime.toLocalDate(),
        dischargeDetails = restrictivePatient.commentText
      )
    )
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
