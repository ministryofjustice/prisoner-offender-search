package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictedPatientDto
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonerSyncServiceTest {
  private val nomisService: NomisService = mock()
  private val restrictedPatientService: RestrictedPatientService = mock()
  private val prisonerIndexService: PrisonerIndexService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private lateinit var prisonerSyncService: PrisonerSyncService

  @BeforeEach
  fun beforeEach() {
    prisonerSyncService =
      PrisonerSyncService(nomisService, restrictedPatientService, prisonerIndexService, telemetryClient)

    whenever(nomisService.getOffender(anyLong())).thenReturn(makeOffenderBooking())
    whenever(nomisService.getOffender(anyString())).thenReturn(makeOffenderBooking())
  }

  @Nested
  inner class WithRestrictedPatient {
    @Test
    fun `calls restricted patient service when the offender is OUT`() {
      prisonerSyncService.withRestrictedPatientIfOut(makeOffenderBooking())

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `skip call to restricted patient service when the offender is not currently out`() {
      prisonerSyncService.withRestrictedPatientIfOut(
        makeOffenderBooking(
          assignedLivingUnit = AssignedLivingUnit(
            agencyName = "MDI",
            agencyId = "MDI",
            description = "Moorland",
            locationId = 2
          )
        )
      )

      verify(restrictedPatientService, never()).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `calls withRestrictedPatientIfOut on externalMovement`() {
      prisonerSyncService.externalMovement(
        ExternalPrisonerMovementMessage(
          bookingId = 123,
          movementSeq = 1,
          offenderIdDisplay = "123",
          fromAgencyLocationId = "OUT",
          toAgencyLocationId = "INT",
          directionCode = "OUT",
          movementType = "Test"
        )
      )
      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Nested
    inner class Mapping {
      @Test
      fun `maps the restricted patient data`() {
        val prison = Agency(agencyId = "MDI", agencyType = "INST", active = true)
        val hospital = Agency(agencyId = "HAZLWD", agencyType = "HSHOSP", active = true)
        val now = LocalDateTime.now()

        whenever(restrictedPatientService.getRestrictedPatient(anyString())).thenReturn(
          RestrictedPatientDto(
            id = 1,
            prisonerNumber = "A1234AA",
            fromLocation = prison,
            supportingPrison = prison,
            hospitalLocation = hospital,
            dischargeTime = now,
            commentText = "test"
          )
        )

        val offenderBooking = prisonerSyncService.withRestrictedPatientIfOut(makeOffenderBooking())

        assertThat(offenderBooking.restrictivePatient)
          .extracting("supportingPrison", "dischargedHospital", "dischargeDate", "dischargeDetails")
          .contains(prison, hospital, now.toLocalDate(), "test")
      }

      @Test
      fun `handle no restricted patient`() {
        whenever(restrictedPatientService.getRestrictedPatient(anyString())).thenReturn(null)

        val offenderBooking = prisonerSyncService.withRestrictedPatientIfOut(makeOffenderBooking())

        assertThat(offenderBooking.restrictivePatient).isNull()
      }
    }


    @Test
    fun `calls withRestrictedPatientIfOut on offenderBookingChange`() {
      prisonerSyncService.offenderBookingChange(OffenderBookingChangedMessage(1000L))
      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `calls withRestrictedPatientIfOut on offenderBookNumberChange`() {
      prisonerSyncService.offenderBookNumberChange(OffenderBookingChangedMessage(1000L))
      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `calls withRestrictedPatientIfOut on offenderChange`() {
      prisonerSyncService.offenderChange(
        OffenderChangedMessage(
          eventType = "APP",
          offenderId = 1L,
          offenderIdDisplay = "A1234AA"
        )
      )
      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

  }

  private fun makeOffenderBooking(
    assignedLivingUnit: AssignedLivingUnit = AssignedLivingUnit(
      agencyId = "OUT",
      locationId = 1,
      description = "OUT",
      agencyName = "OUT"
    )
  ) = OffenderBooking(
    "A1234AA",
    "Fred",
    "Bloggs",
    LocalDate.of(1976, 5, 15),
    false,
    assignedLivingUnit = assignedLivingUnit
  )


}
