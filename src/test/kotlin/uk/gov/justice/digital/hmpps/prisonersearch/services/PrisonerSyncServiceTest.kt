package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.LocalDate
import java.time.LocalDateTime

const val prisonerNumber = "A1234AA"

class PrisonerSyncServiceTest {

  private val nomisService = mock<NomisService>()
  private val prisonerIndexService = mock<PrisonerIndexService>()
  private val incentivesService = mock<IncentivesService>()
  private val telemetryClient = mock<TelemetryClient>()

  private val prisonerSyncService = PrisonerSyncService(nomisService, prisonerIndexService, incentivesService, telemetryClient)

  @Nested
  inner class MaybeDeleteOffender {

    @Test
    fun `sync on delete event if prisoner exists`() {
      val offenderBooking = makeOffenderBooking()
      val incentiveLevel = anIncentiveLevel()
      whenever(nomisService.getOffender(prisonerNumber)).thenReturn(offenderBooking)
      whenever(incentivesService.getCurrentIncentive(any())).thenReturn(incentiveLevel)

      prisonerSyncService.maybeDeleteOffender(
        OffenderChangedMessage(eventType = "type", offenderId = 1, offenderIdDisplay = prisonerNumber)
      )

      verify(prisonerIndexService).sync(offenderBooking, incentiveLevel)
    }

    @Test
    fun `delete on delete event if prisoner does not exist`() {
      whenever(nomisService.getOffender(prisonerNumber)).thenReturn(null)

      prisonerSyncService.maybeDeleteOffender(
        OffenderChangedMessage(eventType = "type", offenderId = 1, offenderIdDisplay = prisonerNumber)
      )

      verify(prisonerIndexService).delete(prisonerNumber)
    }
  }

  private fun makeOffenderBooking() = OffenderBooking(
    prisonerNumber,
    "Fred",
    "Bloggs",
    LocalDate.of(1976, 5, 15),
    false,
    latestLocationId = "MDI",
    bookingId = 123456L,
  )

  private fun anIncentiveLevel() = IncentiveLevel(
    iepLevel = "Standard",
    iepTime = LocalDateTime.now(),
    nextReviewDate = null,
  )
}
