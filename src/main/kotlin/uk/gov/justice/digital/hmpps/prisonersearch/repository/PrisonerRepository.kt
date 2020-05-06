package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.annotations.Query
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerA
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerB
import java.time.LocalDate

@NoRepositoryBean
interface PrisonerRepository<T:Prisoner, String> : ElasticsearchRepository<T, String> {
    @Query("\"bool\": { \"should\": [ { \"term\" : { \"prisonerNumber\" : \"?0\" } }, { \"term\" : { \"bookNumber\" : \"?0\" } }, { \"term\" : { \"bookingId\" : \"?0\" } }, { \"term\" : { \"pncNumber\" : \"?0\" } }, { \"term\" : { \"lastName\" : \"?0\" } }, { \"term\" : { \"firstName\" : \"?0\" } }, { \"term\" : { \"middleNames\" : \"?0\" } }, { \"term\" : { \"aliases.lastName\" : \"?0\" } }, { \"term\" : { \"aliases.firstName\" : \"?0\" } }, { \"term\" : { \"aliases.middleName\" : \"?0\" } } ], \"minimum_should_match\" : 1 }")
    fun findByKeywords(keywords: String, pageable: Pageable?): Page<T>

    @Query("\"bool\": { \"should\": [ { \"term\" : { \"prisonerNumber\" : \"?0\" } }, { \"term\" : { \"bookNumber\" : \"?0\" } }, { \"term\" : { \"bookingId\" : \"?0\" } }, { \"term\" : { \"pncNumber\" : \"?0\" } }, { \"term\" : { \"lastName\" : \"?0\" } }, { \"term\" : { \"firstName\" : \"?0\" } }, { \"term\" : { \"middleNames\" : \"?0\" } }, { \"term\" : { \"aliases.lastName\" : \"?0\" } }, { \"term\" : { \"aliases.firstName\" : \"?0\" } }, { \"term\" : { \"aliases.middleName\" : \"?0\" } } ], \"filter\": { \"term\" : { \"prisonId\" : \"?1\" } }, \"minimum_should_match\" : 1 }")
    fun findByKeywordsFilterByPrison(keywords: String, prisonId: String, pageable: Pageable?): Page<T>

    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"prisonerNumber\",\"bookNumber\",\"pncNumber\",\"bookingId\"]}}")
    fun findByIds(ids: String): T?
    fun findByDateOfBirth(dateOfBirth : LocalDate, pageable: Pageable?): Page<T>
    fun findByPrisonId(prisonId : String, pageable: Pageable?): Page<T>
    fun findByBookingId(bookingId : Long): T?
}

@Repository
interface PrisonerARepository : PrisonerRepository<PrisonerA, String>

@Repository
interface PrisonerBRepository : PrisonerRepository<PrisonerB, String>