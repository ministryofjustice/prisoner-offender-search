package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerRepository
import java.time.LocalDate

@Service
class PrisonerSearchService(
    val prisonerARepository: PrisonerARepository,
    val prisonerBRepository: PrisonerBRepository,
    val indexStatusService: IndexStatusService
) {

  fun findById(id: String): Prisoner? {
    return getPrisonerRepository().findByIds(id)
  }

  fun findByKeywords(keywords: String, pageable: Pageable): Page<Prisoner> {
    return getPrisonerRepository().findByKeywords(keywords, pageable)
  }

  fun findByDob(dob: LocalDate, pageable: Pageable): Page<Prisoner> {
    return getPrisonerRepository().findByDateOfBirth(dob, pageable)
  }

  fun findByBookingId(bookingId: Long): Prisoner? {
    return getPrisonerRepository().findByBookingId(bookingId)
  }

  fun findByPrisonId(prisonId: String, pageable: Pageable): Page<Prisoner> {
    return getPrisonerRepository().findByPrisonId(prisonId, pageable)
  }


  fun getPrisonerRepository(): PrisonerRepository<Prisoner, String> {
    if (indexStatusService.getCurrentIndex().currentIndex == SyncIndex.INDEX_A) {
      return prisonerARepository as PrisonerRepository<Prisoner, String>
    } else {
      return prisonerBRepository as PrisonerRepository<Prisoner, String>
    }
  }
}