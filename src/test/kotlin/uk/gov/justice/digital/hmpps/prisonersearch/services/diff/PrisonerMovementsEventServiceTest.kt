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

  private fun newPrisoner() = prisoner("/receive-state-changes/new-prisoner.json")
  private fun prisonerInWithBooking() = prisoner("/receive-state-changes/first-new-booking.json")
  private fun prisonerBeingTransferred() = prisoner("/receive-state-changes/transfer-out.json")
  private fun prisonerTransferredIn(prisonId: String = "WWI") =
    prisoner("/receive-state-changes/transfer-in.json").apply {
      this.prisonId = prisonId
    }

  private fun prisoner(resource: String): Prisoner =
    objectMapper.readValue(resource.readResourceAsText(), Prisoner::class.java)
}

private fun String.readResourceAsText(): String =
  PrisonerMovementsEventServiceTest::class.java.getResource(this)!!.readText()
