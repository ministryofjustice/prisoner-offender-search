package uk.org.justice.digital.hmpps.prisonersearch.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.org.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.org.justice.digital.hmpps.prisonersearch.repository.PrisonerRepository

@Service
class PrisonerIndexService(val prisonerRepository: PrisonerRepository) {

    fun save(prisoner : Prisoner) : Prisoner {
        return prisonerRepository.save(prisoner)
    }

    fun findByPrisonerId(prisonerId : String) : Prisoner {
        return prisonerRepository.findByPrisonerId(prisonerId)
    }

    fun findByLastAndFirstName(lastName : String, firstName : String) : Page<Prisoner> {
        return prisonerRepository.findByLastNameAndFirstName(lastName, firstName, Pageable.unpaged())
    }
}