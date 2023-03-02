package uk.gov.justice.digital.hmpps.prisonersearch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRestClientAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication(
  exclude = [
    ReactiveElasticsearchRestClientAutoConfiguration::class,
    ReactiveElasticsearchRepositoriesAutoConfiguration::class,
  ],
)
@ConfigurationPropertiesScan
class PrisonerOffenderSearch {

  @Bean
  fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
  runApplication<PrisonerOffenderSearch>(*args)
}
