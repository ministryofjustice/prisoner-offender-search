package uk.gov.justice.digital.hmpps.prisonersearch.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.elasticsearch.ElasticsearchContainer


@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
class ElasticSearchConfig {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun elasticSearchContainer() : ElasticsearchContainer {
    log.info("Starting elasticsearch...")
    val container = ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:6.7.2")
    container.start()
    log.info("Started elasticsearch")
    return container
  }

}
