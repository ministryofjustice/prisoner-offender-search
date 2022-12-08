package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.NEW_ADMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.POST_MERGE_ADMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.RETURN_FROM_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.TEMPORARY_ABSENCE_RETURN
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReleaseReason
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReleaseReason.RELEASED
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReleaseReason.SENT_TO_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReleaseReason.TEMPORARY_ABSENCE_RELEASE
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementInChange
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementInChange.CourtReturn
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementInChange.MergeAdmission
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementInChange.NewAdmission
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementInChange.Readmission
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementInChange.TAPReturn
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementInChange.TransferIn
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementOutChange.Released
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementOutChange.SentToCourt
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementOutChange.TAPRelease
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PossibleMovementChange.MovementOutChange.TransferOut
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.LocalDateTime

@Service
class PrisonerMovementsEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val telemetryClient: TelemetryClient,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    offenderBooking: OffenderBooking
  ) {
    when (val movementChange = calculateMovementChange(previousPrisonerSnapshot, prisoner, offenderBooking)) {
      PossibleMovementChange.None -> {}
      is MovementInChange -> domainEventEmitter.emitPrisonerReceiveEvent(
        offenderNo = movementChange.offenderNo,
        reason = movementChange.reason,
        prisonId = movementChange.prisonId,
      )

      is PossibleMovementChange.MovementOutChange -> domainEventEmitter.emitPrisonerReleaseEvent(
        offenderNo = movementChange.offenderNo,
        reason = movementChange.reason,
        prisonId = movementChange.prisonId,
      )
    }
  }

  private fun calculateMovementChange(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    offenderBooking: OffenderBooking
  ): PossibleMovementChange {
    return previousPrisonerSnapshot.let {
      val prisonerNumber = prisoner.prisonerNumber!!
      if (prisoner.isTransferIn(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isCourtReturn(previousPrisonerSnapshot)) {
        CourtReturn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isNewAdmission(previousPrisonerSnapshot) && isAdmissionAssociatedWithAMerge(offenderBooking)) {
        MergeAdmission(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isNewAdmission(previousPrisonerSnapshot)) {
        NewAdmission(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isReadmission(previousPrisonerSnapshot)) {
        Readmission(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTransferViaCourt(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTAPReturn(previousPrisonerSnapshot)) {
        TAPReturn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTransferViaTAP(previousPrisonerSnapshot)) {
        TransferIn(prisonerNumber, prisoner.prisonId!!)
      } else if (prisoner.isTransferOut(previousPrisonerSnapshot)) {
        TransferOut(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
      } else if (prisoner.isCourtOutMovement(previousPrisonerSnapshot)) {
        SentToCourt(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
      } else if (prisoner.isTAPOutMovement(previousPrisonerSnapshot)) {
        TAPRelease(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
      } else if (prisoner.isRelease(previousPrisonerSnapshot)) {
        Released(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
      } else if (
        prisoner.isSomeOtherMovementIn(previousPrisonerSnapshot) ||
        prisoner.isSomeOtherMovementOut(previousPrisonerSnapshot)
      ) {
        PossibleMovementChange.None.also {
          // really can't think a scenario where will hit this line, so lets log since it means
          // we are not dealing with all scenarios correctly
          telemetryClient.trackEvent(
            "POSPrisonerUpdatedEventsUnknownMovement",
            mapOf("offenderNo" to prisonerNumber),
            null
          )
        }
      } else {
        PossibleMovementChange.None
      }
    }
  }
}

private fun Prisoner.isTransferIn(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "TRN" && inOutStatus == "IN"

private fun Prisoner.isTransferOut(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "IN" && inOutStatus == "TRN"

private fun Prisoner.isCourtReturn(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "OUT" &&
    previousPrisonerSnapshot.lastMovementTypeCode == "CRT" &&
    this.inOutStatus == "IN" &&
    this.lastMovementTypeCode == "CRT"

private fun Prisoner.isCourtOutMovement(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "IN" &&
    this.inOutStatus == "OUT" &&
    this.lastMovementTypeCode == "CRT"

private fun Prisoner.isTAPReturn(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "OUT" &&
    previousPrisonerSnapshot.lastMovementTypeCode == "TAP" &&
    this.inOutStatus == "IN" &&
    this.lastMovementTypeCode == "TAP"

private fun Prisoner.isTAPOutMovement(previousPrisonerSnapshot: Prisoner?) =
  previousPrisonerSnapshot?.inOutStatus == "IN" &&
    this.inOutStatus == "OUT" &&
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

private fun Prisoner.isRelease(previousPrisonerSnapshot: Prisoner?) =
  this.lastMovementTypeCode == "REL" &&
    this.status == "INACTIVE OUT" &&
    previousPrisonerSnapshot?.active == true

private fun Prisoner.isSomeOtherMovementIn(previousPrisonerSnapshot: Prisoner?) =
  this.inOutStatus == "IN" &&
    this.status != previousPrisonerSnapshot?.status

private fun Prisoner.isSomeOtherMovementOut(previousPrisonerSnapshot: Prisoner?) =
  this.inOutStatus == "OUT" &&
    this.status != previousPrisonerSnapshot?.status

private fun isAdmissionAssociatedWithAMerge(offenderBooking: OffenderBooking): Boolean {
  return offenderBooking.identifiers?.filter { it.type == "MERGED" }
    ?.any { it.whenCreated > LocalDateTime.now().minusMinutes(90 )}
    ?: false
}

sealed class PossibleMovementChange {
  sealed class MovementInChange(
    val offenderNo: String,
    val prisonId: String,
    val reason: HmppsDomainEventEmitter.PrisonerReceiveReason
  ) :
    PossibleMovementChange() {
    class TransferIn(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, TRANSFERRED)
    class CourtReturn(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, RETURN_FROM_COURT)
    class TAPReturn(offenderNo: String, prisonId: String) :
      MovementInChange(offenderNo, prisonId, TEMPORARY_ABSENCE_RETURN)

    class NewAdmission(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, NEW_ADMISSION)
    class MergeAdmission(offenderNo: String, prisonId: String) :
      MovementInChange(offenderNo, prisonId, POST_MERGE_ADMISSION)

    class Readmission(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, READMISSION)
  }

  sealed class MovementOutChange(val offenderNo: String, val prisonId: String, val reason: PrisonerReleaseReason) :
    PossibleMovementChange() {
    class TransferOut(offenderNo: String, prisonId: String) :
      MovementOutChange(offenderNo, prisonId, PrisonerReleaseReason.TRANSFERRED)

    class TAPRelease(offenderNo: String, prisonId: String) :
      MovementOutChange(offenderNo, prisonId, TEMPORARY_ABSENCE_RELEASE)

    class Released(offenderNo: String, prisonId: String) : MovementOutChange(offenderNo, prisonId, RELEASED)
    class SentToCourt(offenderNo: String, prisonId: String) : MovementOutChange(offenderNo, prisonId, SENT_TO_COURT)
  }

  object None : PossibleMovementChange()
}
