package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerARepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerBRepository
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class PrisonerSearchService(val prisonerARepository: PrisonerARepository, val prisonerBRepository: PrisonerBRepository, val indexStatusRepository: IndexStatusRepository) {

    fun findById(id : String) : Prisoner {
        return getPrisonerRepository().findByIds(id)
    }

    fun findByKeywords(keywords : String) : Page<Prisoner> {
        return getPrisonerRepository().findByKeywords(keywords, Pageable.unpaged())
    }

    fun findByDob(dob : LocalDate) : Page<Prisoner> {
        return getPrisonerRepository().findByDateOfBirth(dob, Pageable.unpaged())
    }

    fun getPrisonerRepository() : PrisonerRepository {
        val currentIndexStatus = indexStatusRepository.findById("STATUS")
        if (currentIndexStatus.isEmpty) {
            val indexStatus = IndexStatus("STATUS", "index-1", LocalDateTime.now(), null, true)
            indexStatusRepository.save(indexStatus)
            return prisonerARepository;
        } else {
            if (currentIndexStatus.get().currentIndex == "index-1") {
                return prisonerARepository;
            } else {
                return prisonerBRepository;
            }
        }
    }
}