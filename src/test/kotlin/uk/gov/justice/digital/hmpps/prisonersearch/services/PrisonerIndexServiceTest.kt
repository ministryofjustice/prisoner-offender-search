package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.apache.http.StatusLine
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.IndexOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import uk.gov.justice.digital.hmpps.prisonersearch.config.IndexProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictedPatientDto
import java.time.LocalDate
import java.time.LocalDateTime

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

  private val prisonerIndexService = PrisonerIndexService(
    nomisService,
    prisonerARepository,
    prisonerBRepository,
    indexQueueService,
    indexStatusService,
    searchClient,
    telemetryClient,
    indexProperties,
    restrictedPatientService
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
          true
        )
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
          true
        )
      )

      prisonerIndexService.indexingComplete(true)

      verifyZeroInteractions(indexQueueService)
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
  inner class WithRestrictedPatient {
    @Test
    fun `calls restricted patient service when the offender is OUT`() {
      prisonerIndexService.withRestrictedPatientIfOut(makeOffenderBooking())

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `skip call to restricted patient service when the offender is not currently out`() {
      prisonerIndexService.withRestrictedPatientIfOut(
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

    @Nested
    inner class Mapping {
      @Test
      fun `maps the restricted patient data`() {
        val prison = Agency(agencyId = "MDI", agencyType = "INST", active = true)
        val hospital = Agency(agencyId = "HAZLWD", agencyType = "HSHOSP", active = true)
        val now = LocalDateTime.now()

        whenever(restrictedPatientService.getRestrictedPatient(ArgumentMatchers.anyString())).thenReturn(
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

        val offenderBooking = prisonerIndexService.withRestrictedPatientIfOut(makeOffenderBooking())

        assertThat(offenderBooking.restrictivePatient)
          .extracting("supportingPrison", "dischargedHospital", "dischargeDate", "dischargeDetails")
          .contains(prison, hospital, now.toLocalDate(), "test")
      }

      @Test
      fun `handle no restricted patient`() {
        whenever(restrictedPatientService.getRestrictedPatient(ArgumentMatchers.anyString())).thenReturn(null)

        val offenderBooking = prisonerIndexService.withRestrictedPatientIfOut(makeOffenderBooking())

        assertThat(offenderBooking.restrictivePatient).isNull()
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
}
