package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerDifferencesRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class PrisonerDifferencesService(
  private val prisonerDifferencesRepository: PrisonerDifferencesRepository,
) {
  fun retrieveDifferences(from: Instant, to: Instant): List<PrisonerDifferences> =
    prisonerDifferencesRepository.findByDateTimeBetween(from, to)

  @Transactional
  fun deleteOldData(): Int =
    prisonerDifferencesRepository.deleteByDateTimeBefore(Instant.now().minus(28, ChronoUnit.DAYS))
}
