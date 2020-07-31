package uk.gov.justice.digital.hmpps.prisonersearch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRestClientAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
  exclude = [ReactiveElasticsearchRestClientAutoConfiguration::class,
    ReactiveElasticsearchRepositoriesAutoConfiguration::class]
)
class PrisonerOffenderSearch

fun main(args: Array<String>) {
  runApplication<PrisonerOffenderSearch>(*args)
}
