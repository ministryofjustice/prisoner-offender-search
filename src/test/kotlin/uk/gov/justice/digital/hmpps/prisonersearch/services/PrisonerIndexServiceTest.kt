package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.http.StatusLine
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.IndexOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import uk.gov.justice.digital.hmpps.prisonersearch.config.IndexProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerA
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import uk.gov.justice.digital.hmpps.prisonersearch.services.diff.PrisonerDifferenceService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictedPatientDto
import java.lang.RuntimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

class PrisonerIndexServiceTest {

  private val nomisService = mock<NomisService>()
  private val prisonerARepository = mock<PrisonerARepository>()
  private val prisonerBRepository = mock<PrisonerBRepository>()
  private val indexQueueService = mock<IndexQueueService>()
  private val indexStatusService = mock<IndexStatusService>()
  private val searchClient = mock<SearchClient>()
  private val telemetryClient = mock<TelemetryClient>()
  private val indexProperties = mock<IndexProperties>()
  private val restrictedPatientService = mock<RestrictedPatientService>()
  private val prisonerDifferenceService = mock<PrisonerDifferenceService>()
  private val incentivesService = mock<IncentivesService>()

  private val prisonerIndexService = PrisonerIndexService(
    nomisService,
    prisonerARepository,
    prisonerBRepository,
    indexQueueService,
    indexStatusService,
    searchClient,
    telemetryClient,
    indexProperties,
    restrictedPatientService,
    prisonerDifferenceService,
    incentivesService,
  )

  @Nested
  inner class IndexingComplete {

    @Test
    fun `clear messages if index build is complete`() {
      whenever(indexStatusService.markRebuildComplete()).thenReturn(true)
      whenever(indexStatusService.getCurrentIndex()).thenReturn(
        IndexStatus(
          "STATUS",
          SyncIndex.INDEX_A,
          null,
          null,
          true,
        ),
      )

      prisonerIndexService.indexingComplete(true)

      verify(indexQueueService).clearAllMessages()
    }

    @Test
    fun `do not clear messages if index build is not complete`() {
      whenever(indexStatusService.markRebuildComplete()).thenReturn(false)
      whenever(indexStatusService.getCurrentIndex()).thenReturn(
        IndexStatus(
          "STATUS",
          SyncIndex.INDEX_A,
          null,
          null,
          true,
        ),
      )

      prisonerIndexService.indexingComplete(true)

      verifyNoInteractions(indexQueueService)
    }
  }

  @Nested
  inner class CheckExistsAndReset {

    private val restClient = mock<RestClient>()
    private val elasticsearchOperations = mock<ElasticsearchOperations>()
    private val indexOperations = mock<IndexOperations>()

    @BeforeEach
    fun `mock ES clients`() {
      whenever(searchClient.lowLevelClient()).thenReturn(restClient)
      whenever(searchClient.elasticsearchOperations()).thenReturn(elasticsearchOperations)
      whenever(elasticsearchOperations.indexOps(any<IndexCoordinates>())).thenReturn(indexOperations)
    }

    @Test
    fun `waits for index to be gone before trying to recreate it`() {
      val indexExists = mockIndexExists()
      val indexMissing = mockIndexMissing()

      whenever(restClient.performRequest(Request("HEAD", "/prisoner-search-a")))
        .thenReturn(indexExists)
        .thenReturn(indexExists)
        .thenReturn(indexMissing)

      prisonerIndexService.checkExistsAndReset(SyncIndex.INDEX_A)

      verify(restClient).performRequest(Request("DELETE", "/prisoner-search-a"))
      verify(restClient, times(3)).performRequest(Request("HEAD", "/prisoner-search-a"))
      verify(indexOperations).create()
    }

    private fun mockIndexMissing(): Response {
      val checkExistsBadResponse = mock<Response>()
      val checkExistsBadStatusLine = mock<StatusLine>()
      whenever(checkExistsBadResponse.statusLine).thenReturn(checkExistsBadStatusLine)
      whenever(checkExistsBadStatusLine.statusCode).thenReturn(404)
      return checkExistsBadResponse
    }

    private fun mockIndexExists(): Response {
      val checkExistsGoodResponse = mock<Response>()
      val checkExistsGoodStatusLine = mock<StatusLine>()
      whenever(checkExistsGoodResponse.statusLine).thenReturn(checkExistsGoodStatusLine)
      whenever(checkExistsGoodStatusLine.statusCode).thenReturn(200)
      return checkExistsGoodResponse
    }
  }

  @Nested
  inner class SyncPrisoner {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false))
      whenever(prisonerARepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    internal fun `will get incentive level if booking present`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")

      verify(incentivesService).getCurrentIncentive(123456L)
    }

    @Test
    internal fun `will not get incentive if there is no booking`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderWithNoBooking())
      prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will update index with current incentive level`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      whenever(incentivesService.getCurrentIncentive(123456L)).thenReturn(
        IncentiveLevel(
          iepCode = "STD",
          iepLevel = "Standard",
          iepTime = LocalDateTime.parse("2021-01-01T10:00:00.169539"),
          nextReviewDate = LocalDate.parse("2021-03-01"),
        ),
      )

      prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")
      verify(prisonerARepository).save(
        check {
          assertThat(it.currentIncentive?.level?.code).isEqualTo("STD")
          assertThat(it.currentIncentive?.level?.description).isEqualTo("Standard")
          assertThat(it.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T10:00:00"))
          assertThat(it.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2021-03-01"))
        },
      )
    }

    @Test
    internal fun `will update index even if it can't get the current incentive level`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      whenever(incentivesService.getCurrentIncentive(123456L)).thenThrow(RuntimeException("oops"))

      assertThrows<RuntimeException> {
        prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")
        verify(prisonerARepository).save(any())
      }
    }

    @Test
    internal fun `will get restricted patients data is prisoner OUT`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsOut())
      prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `skip call to restricted patient service when the offender is not currently out`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsIn())
      prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")

      verify(restrictedPatientService, never()).getRestrictedPatient("A1234AA")
    }

    @Test
    internal fun `OUT prisoners do not need to have restricted patients data`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsOut())
      whenever(restrictedPatientService.getRestrictedPatient("A1234AA")).thenReturn(null)

      prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")

      verify(prisonerARepository).save(
        check {
          assertThat(it.restrictedPatient).isFalse()
        },
      )
    }

    @Test
    internal fun `OUT prisoners might have restricted patients data`() {
      val prison = Agency(agencyId = "LEI", agencyType = "INST", active = true)
      val hospital = Agency(agencyId = "HAZLWD", agencyType = "HSHOSP", active = true, description = "Hazelwood Hospital")
      val now = LocalDateTime.now()

      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsOut())
      whenever(restrictedPatientService.getRestrictedPatient("A1234AA")).thenReturn(
        RestrictedPatientDto(
          id = 1,
          prisonerNumber = "A1234AA",
          supportingPrison = prison,
          hospitalLocation = hospital,
          dischargeTime = now,
          commentText = "Getting worse",
        ),
      )

      prisonerIndexService.syncPrisoner(prisonerId = "A1234AA")

      verify(prisonerARepository).save(
        check {
          assertThat(it.restrictedPatient).isTrue
          assertThat(it.supportingPrisonId).isEqualTo("LEI")
          assertThat(it.dischargedHospitalId).isEqualTo("HAZLWD")
          assertThat(it.dischargedHospitalDescription).isEqualTo("Hazelwood Hospital")
          assertThat(it.dischargeDate).isEqualTo(now.toLocalDate())
          assertThat(it.dischargeDetails).isEqualTo("Getting worse")
          assertThat(it.locationDescription).isEqualTo("OUT - discharged to Hazelwood Hospital")
        },
      )
    }
  }

  @Nested
  inner class IndexPrisoner {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false))
      whenever(prisonerBRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    internal fun `will get incentive level if booking present`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      prisonerIndexService.indexPrisoner(prisonerId = "A1234AA")

      verify(incentivesService).getCurrentIncentive(123456L)
    }

    @Test
    internal fun `will not get incentive if there is no booking`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderWithNoBooking())
      prisonerIndexService.indexPrisoner(prisonerId = "A1234AA")

      verifyNoInteractions(incentivesService)
    }
  }

  @Nested
  inner class CompareAndMaybeIndexPrisoner {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false))
      whenever(prisonerARepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    internal fun `will get incentive level if booking present`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verify(incentivesService).getCurrentIncentive(123456L)
    }

    @Test
    internal fun `will not get incentive if there is no booking`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderWithNoBooking())
      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will do nothing if no differences found`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(false)
      whenever(prisonerARepository.findById(any())).thenReturn(Optional.of(PrisonerA()))

      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verify(prisonerARepository, never()).save(any())
      verify(prisonerDifferenceService).prisonerHasChanged(any(), any())
      verifyNoMoreInteractions(prisonerDifferenceService)
    }

    @Test
    internal fun `will report differences if found`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)
      whenever(prisonerARepository.findById(any())).thenReturn(Optional.of(PrisonerA()))

      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verify(prisonerDifferenceService).reportDiffTelemetry(any(), any())
      verify(prisonerDifferenceService).handleDifferences(any(), any(), any())
    }

    @Test
    internal fun `will update index with current incentive level`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderBooking(123456L))
      whenever(incentivesService.getCurrentIncentive(123456L)).thenReturn(
        IncentiveLevel(
          iepCode = "STD",
          iepLevel = "Standard",
          iepTime = LocalDateTime.parse("2021-01-01T10:00:00.169539"),
          nextReviewDate = LocalDate.parse("2021-03-01"),
        ),
      )
      whenever(prisonerARepository.findById(any())).thenReturn(Optional.of(PrisonerA()))
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)

      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")
      verify(prisonerARepository).save(
        check {
          assertThat(it.currentIncentive?.level?.code).isEqualTo("STD")
          assertThat(it.currentIncentive?.level?.description).isEqualTo("Standard")
          assertThat(it.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T10:00:00"))
          assertThat(it.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2021-03-01"))
        },
      )
    }

    @Test
    internal fun `will get restricted patients data is prisoner OUT`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsOut())
      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `skip call to restricted patient service when the offender is not currently out`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsIn())
      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verify(restrictedPatientService, never()).getRestrictedPatient("A1234AA")
    }

    @Test
    internal fun `OUT prisoners do not need to have restricted patients data`() {
      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsOut())
      whenever(restrictedPatientService.getRestrictedPatient("A1234AA")).thenReturn(null)
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)
      whenever(prisonerARepository.findById(any())).thenReturn(Optional.of(PrisonerA()))

      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verify(prisonerARepository).save(
        check {
          assertThat(it.restrictedPatient).isFalse()
        },
      )
    }

    @Test
    internal fun `OUT prisoners might have restricted patients data`() {
      val prison = Agency(agencyId = "LEI", agencyType = "INST", active = true)
      val hospital = Agency(agencyId = "HAZLWD", agencyType = "HSHOSP", active = true, description = "Hazelwood Hospital")
      val now = LocalDateTime.now()

      whenever(nomisService.getOffender("A1234AA")).thenReturn(anOffenderThatIsOut())
      whenever(restrictedPatientService.getRestrictedPatient("A1234AA")).thenReturn(
        RestrictedPatientDto(
          id = 1,
          prisonerNumber = "A1234AA",
          supportingPrison = prison,
          hospitalLocation = hospital,
          dischargeTime = now,
          commentText = "Getting worse",
        ),
      )
      whenever(prisonerARepository.findById(any())).thenReturn(Optional.of(PrisonerA()))
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)

      prisonerIndexService.compareAndMaybeIndexPrisoner(prisonerId = "A1234AA")

      verify(prisonerARepository).save(
        check {
          assertThat(it.restrictedPatient).isTrue
          assertThat(it.supportingPrisonId).isEqualTo("LEI")
          assertThat(it.dischargedHospitalId).isEqualTo("HAZLWD")
          assertThat(it.dischargedHospitalDescription).isEqualTo("Hazelwood Hospital")
          assertThat(it.dischargeDate).isEqualTo(now.toLocalDate())
          assertThat(it.dischargeDetails).isEqualTo("Getting worse")
          assertThat(it.locationDescription).isEqualTo("OUT - discharged to Hazelwood Hospital")
        },
      )
    }
  }
}
private fun anOffenderBooking(bookingId: Long) = OffenderBooking(
  offenderNo = "A1234AA",
  firstName = "Fred",
  lastName = "Bloggs",
  dateOfBirth = LocalDate.of(1976, 5, 15),
  bookingId = bookingId,
  assignedLivingUnit = AssignedLivingUnit(
    agencyId = "MDI",
    locationId = 1,
    description = "Moorland",
    agencyName = "Moorland",
  ),
  activeFlag = true,
)
private fun anOffenderWithNoBooking() = OffenderBooking(
  offenderNo = "A1234AA",
  firstName = "Fred",
  lastName = "Bloggs",
  dateOfBirth = LocalDate.of(1976, 5, 15),
  activeFlag = false,
)

private fun anOffenderThatIsOut(
  assignedLivingUnit: AssignedLivingUnit = AssignedLivingUnit(
    agencyId = "OUT",
    locationId = 1,
    description = "OUT",
    agencyName = "OUT",
  ),
) = OffenderBooking(
  "A1234AA",
  "Fred",
  "Bloggs",
  LocalDate.of(1976, 5, 15),
  false,
  assignedLivingUnit = assignedLivingUnit,
  locationDescription = "OUT",
)
private fun anOffenderThatIsIn(
  assignedLivingUnit: AssignedLivingUnit = AssignedLivingUnit(
    agencyName = "MDI",
    agencyId = "MDI",
    description = "Moorland",
    locationId = 2,
  ),
) = OffenderBooking(
  "A1234AA",
  "Fred",
  "Bloggs",
  LocalDate.of(1976, 5, 15),
  false,
  assignedLivingUnit = assignedLivingUnit,
)
