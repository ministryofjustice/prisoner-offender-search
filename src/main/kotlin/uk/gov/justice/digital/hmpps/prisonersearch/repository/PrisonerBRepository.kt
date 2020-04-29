package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.annotations.Query
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerB
import java.time.LocalDate

interface PrisonerBRepository : ElasticsearchRepository<PrisonerB, String>, PrisonerRepository {
    @Query("{\"multi_match\": {\n" +
        "                        \"query\": \"smithy robert\",\n" +
        "                        \"fields\": [\n" +
        "                            \"firstName^30\",\n" +
        "                            \"lastName^70\"\n" +
        "                        ],\n" +
        "                        \"fuzziness\": \"AUTO\",\n" +
        "                        \"type\": \"most_fields\",\n" +
        "                        \"minimum_should_match\": \"50%\",\n" +
        "                        \"boost\": 25\n" +
        "                    }}")
    override fun findByKeywords(keywords: String, pageable: Pageable?): Page<Prisoner>

    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"prisonerId\",\"bookingNo\"]}}")
    override fun findByIds(prisonerId: String): Prisoner

    override fun findByDateOfBirth(dateOfBirth : LocalDate, pageable: Pageable?): Page<Prisoner>
}