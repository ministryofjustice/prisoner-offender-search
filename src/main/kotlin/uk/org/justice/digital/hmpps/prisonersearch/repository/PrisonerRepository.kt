package uk.org.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import uk.org.justice.digital.hmpps.prisonersearch.model.Prisoner

interface PrisonerRepository : ElasticsearchRepository<Prisoner, String> {
    fun findByLastNameAndFirstName(lastName: String, firstName: String, pageable: Pageable?): Page<Prisoner>

    fun findByPrisonerId(prisonerId: String): Prisoner
}