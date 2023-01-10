package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.LocalDate

class PrisonerDiffServiceIntTest : IntegrationTest() {

  private val offenderBooking = offenderBooking()
  private val oldOffenderBooking = offenderBooking("Old First Name")
  private val prisoner = prisoner()
  private val oldPrisoner = prisoner(firstName = "Old First Name")

  @Test
  fun `should handle multiple concurrent updates but only 1 should succeed`() {
    prisonerDifferenceService.handleDifferences(null, offenderBooking, oldPrisoner)
    val oldHash = prisonerEventHashRepository.findByNomsNumber(prisoner.prisonerNumber!!)

    runBlocking {
      repeat(10) {
        async(Dispatchers.Default) {
          prisonerDifferenceService.handleDifferences(
            oldPrisoner,
            offenderBooking,
            prisoner
          )
        }
      }
    }

    val savedHash = prisonerEventHashRepository.findByNomsNumber(prisoner.prisonerNumber!!)
    assertThat(savedHash?.prisonerHash).isNotEqualTo(oldHash?.prisonerHash)
    verify(hmppsDomainEventEmitter).emitPrisonerDifferenceEvent(eq("A1234AA"), any())
    verify(telemetryClient, times(9)).trackEvent(eq("POSPrisonerUpdatedNoChange"), any(), anyOrNull())
  }

  @ParameterizedTest
  @MethodSource("multipleRunIds")
  fun `should handle multiple concurrent updates but only 1 should succeed - using the indexer`() {
    prisonerIndexService.reindex(oldOffenderBooking)
    val oldHash = prisonerEventHashRepository.findByNomsNumber(prisoner.prisonerNumber!!)
    reset(hmppsDomainEventEmitter)
    reset(telemetryClient)

    runBlocking {
      repeat(2) {
        async(Dispatchers.Default) { prisonerIndexService.reindex(offenderBooking) }
      }
    }

    val savedHash = prisonerEventHashRepository.findByNomsNumber(prisoner.prisonerNumber!!)
    assertThat(savedHash?.prisonerHash).isNotEqualTo(oldHash?.prisonerHash)
    verify(hmppsDomainEventEmitter).emitPrisonerDifferenceEvent(eq("A1234AA"), any())
    verify(telemetryClient, times(1)).trackEvent(eq("POSPrisonerUpdatedNoChange"), any(), anyOrNull())
  }

  private fun offenderBooking(firstName: String = "First") = OffenderBooking(
    offenderNo = "A1234AA",
    firstName = firstName,
    lastName = "Last",
    dateOfBirth = LocalDate.of(1990, 1, 1),
    activeFlag = true,
  )

  private fun prisoner(firstName: String = "First") = Prisoner().apply {
    prisonerNumber = "A1234AA"
    this.firstName = firstName
    lastName = "Last"
    dateOfBirth = LocalDate.of(1990, 1, 1)
  }

  companion object {
    @JvmStatic
    fun multipleRunIds() = (1..20).map { Arguments.of(it.toString()) }
  }
}
