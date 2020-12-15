package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus

interface IndexStatusRepository : ElasticsearchRepository<IndexStatus, String>
