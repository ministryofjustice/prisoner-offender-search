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
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.IndexOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.config.IndexProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerA
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictedPatientDto
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
  private val diffProperties = mock<DiffProperties>()
  private val domainEventsEmitter = mock<HmppsDomainEventEmitter>()

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
    diffProperties,
    domainEventsEmitter
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
        val hospital = Agency(agencyId = "HAZLWD", agencyType = "HSHOSP", active = true)
        val now = LocalDateTime.now()

        whenever(restrictedPatientService.getRestrictedPatient(anyString())).thenReturn(
          RestrictedPatientDto(
            id = 1,
            prisonerNumber = "A1234AA",
            hospitalLocation = hospital,
            dischargeTime = now,
            commentText = "test"
          )
        )

        val offenderBooking = prisonerIndexService.withRestrictedPatientIfOut(makeOffenderBooking())

        assertThat(offenderBooking.restrictivePatient)
          .extracting("supportingPrisonId", "dischargedHospital", "dischargeDate", "dischargeDetails")
          .contains("MDI", hospital, now.toLocalDate(), "test")
      }

      @Test
      fun `handle no restricted patient`() {
        whenever(restrictedPatientService.getRestrictedPatient(anyString())).thenReturn(null)

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
      assignedLivingUnit = assignedLivingUnit,
      latestLocationId = "MDI"
    )
  }

  @Nested
  inner class SyncPrisonerDifferencesTelemetry {
    private val savedPrisoner = PrisonerA().apply { pncNumber = "somePncNumber2" }
    @BeforeEach
    fun setUp() {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(
        IndexStatus(currentIndex = SyncIndex.INDEX_A, inProgress = false, startIndexTime = null, endIndexTime = null)
      )
      whenever(prisonerARepository.save(any())).thenReturn(savedPrisoner)
      whenever(diffProperties.telemetry).thenReturn(true)
    }

    @Test
    fun `should raise telemetry if prisoner updated`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(PrisonerA().apply { pncNumber = "somePncNumber1" }))

      prisonerIndexService.sync(someOffenderBooking())

      verify(telemetryClient).trackEvent(eq("POSPrisonerUpdated"), anyMap(), isNull())
    }

    @Test
    fun `should not raise telemetry if prisoner is new`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.empty())

      prisonerIndexService.sync(someOffenderBooking())

      verify(telemetryClient, never()).trackEvent(eq("POSPrisonerUpdated"), anyMap(), isNull())
    }

    @Test
    fun `should handle exceptions when generating telemetry`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(PrisonerA().apply { pncNumber = "somePncNumber1" }))
      whenever(telemetryClient.trackEvent(anyString(), anyMap(), any())).thenThrow(RuntimeException::class.java)

      val saved = prisonerIndexService.sync(someOffenderBooking())

      assertThat(saved).isEqualTo(savedPrisoner)
    }

    @Test
    fun `should not produce telemetry if feature switch is off`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(PrisonerA().apply { pncNumber = "somePncNumber1" }))
      whenever(diffProperties.telemetry).thenReturn(false)

      prisonerIndexService.sync(someOffenderBooking())

      verify(telemetryClient, never()).trackEvent(eq("POSPrisonerUpdated"), anyMap(), isNull())
    }
  }

  @Nested
  inner class SyncPrisonerCreatedTelemetry {
    private val savedPrisoner = PrisonerA().apply { pncNumber = "somePncNumber2" }
    @BeforeEach
    fun setUp() {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(
        IndexStatus(currentIndex = SyncIndex.INDEX_A, inProgress = false, startIndexTime = null, endIndexTime = null)
      )
      whenever(prisonerARepository.save(any())).thenReturn(savedPrisoner)
      whenever(diffProperties.telemetry).thenReturn(true)
    }

    @Test
    fun `should raise telemetry if prisoner new`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.empty())

      prisonerIndexService.sync(someOffenderBooking())

      verify(telemetryClient).trackEvent(eq("POSPrisonerCreated"), anyMap(), isNull())
    }

    @Test
    fun `should not raise telemetry if prisoner exists`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(PrisonerA().apply { pncNumber = "somePncNumber1" }))

      prisonerIndexService.sync(someOffenderBooking())

      verify(telemetryClient, never()).trackEvent(eq("POSPrisonerCreated"), anyMap(), isNull())
    }

    @Test
    fun `should handle exceptions when generating telemetry`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.empty())
      whenever(telemetryClient.trackEvent(anyString(), anyMap(), any())).thenThrow(RuntimeException::class.java)

      val saved = prisonerIndexService.sync(someOffenderBooking())

      assertThat(saved).isEqualTo(savedPrisoner)
    }

    @Test
    fun `should not produce telemetry if feature switch is off`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.empty())
      whenever(diffProperties.telemetry).thenReturn(false)

      prisonerIndexService.sync(someOffenderBooking())

      verify(telemetryClient, never()).trackEvent(eq("POSPrisonerCreated"), anyMap(), isNull())
    }
  }

  @Nested
  inner class SyncPrisonerDifferencesEvent {
    private val savedPrisoner = PrisonerA().apply { pncNumber = "somePncNumber2" }
    @BeforeEach
    fun setUp() {
      whenever(indexStatusService.getCurrentIndex()).thenReturn(
        IndexStatus(currentIndex = SyncIndex.INDEX_A, inProgress = false, startIndexTime = null, endIndexTime = null)
      )
      whenever(prisonerARepository.save(any())).thenReturn(savedPrisoner)
      whenever(diffProperties.events).thenReturn(true)
    }

    @Test
    fun `should send event if prisoner updated`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(PrisonerA().apply { pncNumber = "somePncNumber1" }))

      prisonerIndexService.sync(someOffenderBooking())

      verify(domainEventsEmitter).emitPrisonerDifferenceEvent(eq("someOffenderNo"), anyMap())
    }

    @Test
    fun `should not send event if prisoner is new`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.empty())

      prisonerIndexService.sync(someOffenderBooking())

      verify(domainEventsEmitter, never()).emitPrisonerDifferenceEvent(eq("someOffenderNo"), anyMap())
    }

    @Test
    fun `should not send event if there are no differences`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(savedPrisoner))

      prisonerIndexService.sync(someOffenderBooking())

      verify(domainEventsEmitter, never()).emitPrisonerDifferenceEvent(eq("someOffenderNo"), anyMap())
    }

    @Test
    fun `should handle exceptions when sending events`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(PrisonerA().apply { pncNumber = "somePncNumber1" }))
      whenever(domainEventsEmitter.emitPrisonerDifferenceEvent(anyString(), anyMap())).thenThrow(RuntimeException::class.java)

      val saved = prisonerIndexService.sync(someOffenderBooking())

      assertThat(saved).isEqualTo(savedPrisoner)
    }

    @Test
    fun `should not send event if feature switch is off`() {
      whenever(prisonerARepository.findById(anyString())).thenReturn(Optional.of(PrisonerA().apply { pncNumber = "somePncNumber1" }))
      whenever(diffProperties.events).thenReturn(false)

      prisonerIndexService.sync(someOffenderBooking())

      verify(domainEventsEmitter, never()).emitPrisonerDifferenceEvent(eq("someOffenderNo"), anyMap())
    }
  }

  private fun someOffenderBooking() =
    OffenderBooking(
      offenderNo = "someOffenderNo",
      firstName = "someFirstName",
      lastName = "someLastName",
      dateOfBirth = LocalDate.now(),
      activeFlag = true
    )
}
