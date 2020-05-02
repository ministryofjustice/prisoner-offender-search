package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.annotations.Query
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.data.repository.NoRepositoryBean
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import java.time.LocalDate

@NoRepositoryBean
interface PrisonerRepository<T:Prisoner, String> : ElasticsearchRepository<T, String> {
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"lastName\", \"middleNames\", \"firstName\"], \"fuzziness\": \"AUTO\"}}")
    fun findByKeywords(keywords: String, pageable: Pageable?): Page<T>
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"prisonerNumber\",\"bookNumber\"]}}")
    fun findByIds(ids: String): T?
    fun findByDateOfBirth(dateOfBirth : LocalDate, pageable: Pageable?): Page<T>
    fun findByPrisonId(prisonId : String, pageable: Pageable?): Page<T>
    fun findByBookingId(bookingId : Long): T?
}