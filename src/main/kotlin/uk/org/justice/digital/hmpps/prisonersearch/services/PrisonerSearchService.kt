package uk.org.justice.digital.hmpps.prisonersearch.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.org.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.org.justice.digital.hmpps.prisonersearch.repository.PrisonerRepository
import java.time.LocalDate

@Service
class PrisonerSearchService(val prisonerRepository: PrisonerRepository) {

    fun findByPrisonerId(prisonerId : String) : Prisoner {
        return prisonerRepository.findByPrisonerId(prisonerId)
    }

    fun findByKeywords(keywords : String) : Page<Prisoner> {
        return prisonerRepository.findByKeywords(keywords, Pageable.unpaged())
    }

    fun findByDob(dob : LocalDate) : Page<Prisoner> {
        return prisonerRepository.findByDateOfBirth(dob, Pageable.unpaged())
    }

}