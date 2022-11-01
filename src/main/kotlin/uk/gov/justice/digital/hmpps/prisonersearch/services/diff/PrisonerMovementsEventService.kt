package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED
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
    }
  }

  private fun calculateMovementChange(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner): PossibleMovementChange {
    return previousPrisonerSnapshot.let {
      // TODO: when we do a few more of these we can extract a nice state machine pattern
      if (previousPrisonerSnapshot?.inOutStatus == "TRN" && prisoner.inOutStatus == "IN") {
        TransferIn(prisoner.prisonerNumber!!, prisoner.prisonId!!)
      } else {
        PossibleMovementChange.None
      }
    }
  }
}

sealed class PossibleMovementChange {
  sealed class MovementChange(val offenderNo: String, val reason: HmppsDomainEventEmitter.PrisonerReceiveReason) :
    PossibleMovementChange() {
    class TransferIn(offenderNo: String, val prisonId: String) : MovementChange(offenderNo, TRANSFERRED)
  }

  object None : PossibleMovementChange()
}
