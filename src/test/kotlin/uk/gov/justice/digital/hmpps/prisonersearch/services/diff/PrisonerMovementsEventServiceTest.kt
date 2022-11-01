package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter

const val OFFENDER_NO = "A9460DY"

internal class PrisonerMovementsEventServiceTest {
  private val domainEventsEmitter = mock<HmppsDomainEventEmitter>()

  private val prisonerMovementsEventService = PrisonerMovementsEventService(domainEventsEmitter)

  private val objectMapper = JsonMapper.builder()
    .findAndAddModules()
    .build()

  @Test
  internal fun `will not emit anything if changes are not related to movements`() {
    val previousPrisonerSnapshot = prisonerInWithBooking()
    val prisoner = prisonerInWithBooking().apply {
      this.firstName = "BOBBY"
    }

    prisonerMovementsEventService.generateAnyMovementEvents(previousPrisonerSnapshot, prisoner)

    verifyNoInteractions(domainEventsEmitter)
  }

  @Test
  internal fun `will not emit anything for a new prisoner `() {
    val prisoner = newPrisoner()

    prisonerMovementsEventService.generateAnyMovementEvents(null, prisoner)

    verifyNoInteractions(domainEventsEmitter)
  }

  @Nested
  inner class OutOnTransfer {
    private val previousPrisonerSnapshot = prisonerBeingTransferred()

    @Test
    internal fun `will emit receive event with reason of transfer`() {
      val prisoner = prisonerTransferredIn("WWI")

      prisonerMovementsEventService.generateAnyMovementEvents(previousPrisonerSnapshot, prisoner)

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED,
        prisonId = "WWI",
      )
    }
  }

  @Nested
  inner class OutAtCourt {
    private val previousPrisonerSnapshot = prisonerOutAtCourt()

    @Test
    internal fun `will emit receive event with reason of court return for return to same prison`() {
      val prisoner = prisonerReturnFromCourtSamePrison()

      prisonerMovementsEventService.generateAnyMovementEvents(previousPrisonerSnapshot, prisoner)

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = HmppsDomainEventEmitter.PrisonerReceiveReason.RETURN_FROM_COURT,
        prisonId = "WWI",
      )
    }

    @Test
    internal fun `will emit receive event with reason of transfer for return to different prison`() {
      val prisoner = prisonerReturnFromCourtDifferentPrison("BXI")

      prisonerMovementsEventService.generateAnyMovementEvents(previousPrisonerSnapshot, prisoner)

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED,
        prisonId = "BXI",
      )
    }
  }
  @Nested
  inner class OutOnTAP {
    private val previousPrisonerSnapshot = prisonerOutOnTAP()

    @Test
    internal fun `will emit receive event with reason of TAP return for return to same prison`() {
      val prisoner = prisonerReturnFromTAPSamePrison()

      prisonerMovementsEventService.generateAnyMovementEvents(previousPrisonerSnapshot, prisoner)

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = HmppsDomainEventEmitter.PrisonerReceiveReason.TEMPORARY_ABSENCE_RETURN,
        prisonId = "WWI",
      )
    }

    @Test
    internal fun `will emit receive event with reason of transfer for return to different prison`() {
      val prisoner = prisonerReturnFromTAPDifferentPrison("BXI")

      prisonerMovementsEventService.generateAnyMovementEvents(previousPrisonerSnapshot, prisoner)

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED,
        prisonId = "BXI",
      )
    }
  }

  private fun newPrisoner() = prisoner("/receive-state-changes/new-prisoner.json")
  private fun prisonerInWithBooking() = prisoner("/receive-state-changes/first-new-booking.json")
  private fun prisonerBeingTransferred() = prisoner("/receive-state-changes/transfer-out.json")
  private fun prisonerTransferredIn(prisonId: String = "WWI") =
    prisoner("/receive-state-changes/transfer-in.json").apply {
      this.prisonId = prisonId
    }

  private fun prisonerOutAtCourt() = prisoner("/receive-state-changes/court-out.json")
  private fun prisonerReturnFromCourtSamePrison() = prisoner("/receive-state-changes/court-in-same-prison.json")
  private fun prisonerReturnFromCourtDifferentPrison(prisonId: String = "NMI") =
    prisoner("/receive-state-changes/court-in-different-prison.json").apply { this.prisonId = prisonId }
  private fun prisonerOutOnTAP() = prisoner("/receive-state-changes/tap-out.json")
  private fun prisonerReturnFromTAPSamePrison() = prisoner("/receive-state-changes/tap-in-same-prison.json")
  private fun prisonerReturnFromTAPDifferentPrison(prisonId: String = "NMI") =
    prisoner("/receive-state-changes/tap-in-different-prison.json").apply { this.prisonId = prisonId }

  private fun prisoner(resource: String): Prisoner =
    objectMapper.readValue(resource.readResourceAsText(), Prisoner::class.java)
}

private fun String.readResourceAsText(): String =
  PrisonerMovementsEventServiceTest::class.java.getResource(this)!!.readText()
