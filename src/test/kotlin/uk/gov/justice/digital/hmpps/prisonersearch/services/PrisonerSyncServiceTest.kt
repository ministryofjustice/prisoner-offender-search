package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.http.StatusLine
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.index.IndexService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.IndexOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import uk.gov.justice.digital.hmpps.prisonersearch.config.IndexProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerDifferenceService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictedPatientDto
import java.time.LocalDate
import java.time.LocalDateTime

const val prisonerNumber = "A1234AA"

class PrisonerSyncServiceTest {

  private val nomisService = mock<NomisService>()
  private val prisonerIndexService = mock<PrisonerIndexService>()
  private val telemetryClient = mock<TelemetryClient>()

  private val prisonerSyncService = PrisonerSyncService(nomisService, prisonerIndexService, telemetryClient)

  @Nested
  inner class MaybeDeleteOffender {

    @Test
    fun `sync on delete event if prisoner exists`() {
      val offenderBooking = makeOffenderBooking()
      whenever(nomisService.getOffender(prisonerNumber)).thenReturn(offenderBooking)

      prisonerSyncService.maybeDeleteOffender(
        OffenderChangedMessage(eventType = "type", offenderId = 1, offenderIdDisplay = prisonerNumber)
      )

      verify(prisonerIndexService).sync(offenderBooking)
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
    latestLocationId = "MDI"
  )
}
