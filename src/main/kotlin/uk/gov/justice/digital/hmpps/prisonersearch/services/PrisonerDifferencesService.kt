package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerDifferencesRepository
import java.time.Instant

@Service
class PrisonerDifferencesService(
  private val prisonerDifferencesRepository: PrisonerDifferencesRepository,
) {
  fun retrieveDifferences(from: Instant, to: Instant): List<PrisonerDifferences> =
    prisonerDifferencesRepository.findByDateTimeBetween(from, to)
}
