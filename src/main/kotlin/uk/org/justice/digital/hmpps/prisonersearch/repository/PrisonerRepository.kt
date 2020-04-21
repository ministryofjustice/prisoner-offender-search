package uk.org.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.annotations.Query
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import uk.org.justice.digital.hmpps.prisonersearch.model.Prisoner
import java.time.LocalDate

interface PrisonerRepository : ElasticsearchRepository<Prisoner, String> {
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"lastName\", \"firstName\", \"prisonerId\", \"agencyId\" ], \"fuzziness\": \"AUTO\"}}")
    fun findByKeywords(keywords: String, pageable: Pageable?): Page<Prisoner>

    fun findByPrisonerId(prisonerId: String): Prisoner

    fun findByDateOfBirth(dateOfBirth : LocalDate, pageable: Pageable?): Page<Prisoner>
}