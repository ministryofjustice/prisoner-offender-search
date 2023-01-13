package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.data.repository.CrudRepository
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus

interface IndexStatusRepository : ElasticsearchRepository<IndexStatus, String>, CrudRepository<IndexStatus, String>
