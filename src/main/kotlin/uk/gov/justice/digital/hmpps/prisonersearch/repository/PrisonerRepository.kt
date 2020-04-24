package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import java.time.LocalDate

interface PrisonerRepository {
    fun findByKeywords(keywords: String, pageable: Pageable?): Page<Prisoner>
    fun findByIds(prisonerId: String): Prisoner
    fun findByDateOfBirth(dateOfBirth : LocalDate, pageable: Pageable?): Page<Prisoner>
}