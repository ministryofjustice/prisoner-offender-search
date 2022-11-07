package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.NEW_ADMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.RETURN_FROM_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.TEMPORARY_ABSENCE_RETURN
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementChange.CourtReturn
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementChange.TAPReturn
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementChange.TransferIn

@Service
class PrisonerMovementsEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val telemetryClient: TelemetryClient,
) {
  fun generateAnyMovementEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner
  ) {
    when (val movementChange = calculateMovementChange(previousPrisonerSnapshot, prisoner)) {
      PossibleMovementChange.None -> {}
      is TransferIn -> domainEventEmitter.emitPrisonerReceiveEvent(
        offenderNo = movementChange.offenderNo,
        reason = movementChange.reason,
        prisonId = movementChange.prisonId,
      )

      is CourtReturn -> domainEventEmitter.emitPrisonerReceiveEvent(
        offenderNo = movementChange.offenderNo,
        reason = movementChange.reason,
        prisonId = movementChange.prisonId,
      )

      is TAPReturn -> domainEventEmitter.emitPrisonerReceiveEvent(
        offenderNo = movementChange.offenderNo,
        reason = movementChange.reason,
        prisonId = movementChange.prisonId,
      )

      is PossibleMovementChange.MovementChange.NewAdmission -> domainEventEmitter.emitPrisonerReceiveEvent(
        offenderNo = movementChange.offenderNo,
        reason = movementChange.reason,
        prisonId = movementChange.prisonId,
      )

      is PossibleMovementChange.MovementChange.Readmission -> domainEventEmitter.emitPrisonerReceiveEvent(
        offenderNo = movementChange.offenderNo,
        reason = movementChange.reason,
        prisonId = movementChange.prisonId,
      )
    }
  }

  private fun calculateMovementChange(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner): PossibleMovementChange {
    return previousPrisonerSnapshot.let {
      val prisonerNumber = prisoner.prisonerNumber!!
      if (prisoner.isTransferIn(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isCourtReturn(previousPrisonerSnapshot)) {
        CourtReturn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isNewAdmission(previousPrisonerSnapshot)) {
        PossibleMovementChange.MovementChange.NewAdmission(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isReadmission(previousPrisonerSnapshot)) {
        PossibleMovementChange.MovementChange.Readmission(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTransferViaCourt(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTAPReturn(previousPrisonerSnapshot)) {
        TAPReturn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTransferViaTAP(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isSomeOtherMovementIn(previousPrisonerSnapshot)) {
        PossibleMovementChange.None.also {
          // really can't think a scenario where will hit this line, so lets log since it means
          // we are not dealing with all scenarios correctly
          telemetryClient.trackEvent("POSPrisonerUpdatedEventsUnknownMovement", mapOf("offenderNo" to prisonerNumber), null)
        }
      } else {
        PossibleMovementChange.None
      }
    }
  }
}

private fun Prisoner.isTransferIn(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "TRN" && inOutStatus == "IN"

private fun Prisoner.isCourtReturn(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "OUT" &&
    previousPrisonerSnapshot.lastMovementTypeCode == "CRT" &&
    this.inOutStatus == "IN" &&
    this.lastMovementTypeCode == "CRT"

private fun Prisoner.isTAPReturn(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "OUT" &&
    previousPrisonerSnapshot.lastMovementTypeCode == "TAP" &&
    this.inOutStatus == "IN" &&
    this.lastMovementTypeCode == "TAP"

private fun Prisoner.isTransferViaCourt(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "OUT" &&
    previousPrisonerSnapshot.lastMovementTypeCode == "CRT" &&
    this.inOutStatus == "IN" &&
    this.lastMovementTypeCode == "ADM" &&
    this.lastMovementReasonCode == "TRNCRT"

private fun Prisoner.isTransferViaTAP(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "OUT" &&
    previousPrisonerSnapshot.lastMovementTypeCode == "TAP" &&
    this.inOutStatus == "IN" &&
    this.lastMovementTypeCode == "ADM" &&
    this.lastMovementReasonCode == "TRNTAP"

private fun Prisoner.isNewAdmission(previousPrisonerSnapshot: Prisoner?) =
  this.lastMovementTypeCode == "ADM" &&
    this.status == "ACTIVE IN" &&
    this.bookingId != previousPrisonerSnapshot?.bookingId

private fun Prisoner.isReadmission(previousPrisonerSnapshot: Prisoner?) =
  this.lastMovementTypeCode == "ADM" &&
    this.bookingId == previousPrisonerSnapshot?.bookingId &&
    this.status == "ACTIVE IN" &&
    previousPrisonerSnapshot?.status == "INACTIVE OUT"

private fun Prisoner.isSomeOtherMovementIn(previousPrisonerSnapshot: Prisoner?) =
  this.inOutStatus == "IN" &&
    this.status != previousPrisonerSnapshot?.status

sealed class PossibleMovementChange {
  sealed class MovementChange(val offenderNo: String, val reason: HmppsDomainEventEmitter.PrisonerReceiveReason) :
    PossibleMovementChange() {
    class TransferIn(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, TRANSFERRED)
    class CourtReturn(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, RETURN_FROM_COURT)
    class TAPReturn(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, TEMPORARY_ABSENCE_RETURN)
    class NewAdmission(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, NEW_ADMISSION)
    class Readmission(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, READMISSION)
  }

  object None : PossibleMovementChange()
}
