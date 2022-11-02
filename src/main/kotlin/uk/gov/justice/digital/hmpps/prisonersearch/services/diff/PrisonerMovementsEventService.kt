package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.RETURN_FROM_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.TEMPORARY_ABSENCE_RETURN
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementChange.CourtReturn
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementChange.TAPReturn
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementChange.TransferIn

@Service
class PrisonerMovementsEventService(private val domainEventEmitter: HmppsDomainEventEmitter) {
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
        fromPrisonId = null /* TODO: get previous prisonId from movements API */
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
    }
  }

  private fun calculateMovementChange(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner): PossibleMovementChange {
    return previousPrisonerSnapshot.let {
      val prisonerNumber = prisoner.prisonerNumber!!
      if (prisoner.isTransferIn(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isCourtReturn(previousPrisonerSnapshot)) {
        CourtReturn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTransferViaCourt(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTAPReturn(previousPrisonerSnapshot)) {
        TAPReturn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTransferViaTAP(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
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

sealed class PossibleMovementChange {
  sealed class MovementChange(val offenderNo: String, val reason: HmppsDomainEventEmitter.PrisonerReceiveReason) :
    PossibleMovementChange() {
    class TransferIn(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, TRANSFERRED)
    class CourtReturn(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, RETURN_FROM_COURT)
    class TAPReturn(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, TEMPORARY_ABSENCE_RETURN)
  }

  object None : PossibleMovementChange()
}
