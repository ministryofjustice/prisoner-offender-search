package uk.gov.justice.digital.hmpps.prisonersearch.config

import org.elasticsearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.RestClients
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

@Configuration
@EnableElasticsearchRepositories(basePackages = ["uk.gov.justice.digital.hmpps.prisonersearch.repository"])
class ElasticSearchConfiguration : AbstractElasticsearchConfiguration() {

  @Value("\${elasticsearch.proxy.url}")
  private val url: String? = null

  @Bean("elasticSearchClient")
  override fun elasticsearchClient(): RestHighLevelClient {
    return RestClients.create(ClientConfiguration.builder().connectedTo("${url?.substringAfter("//")}").build()).rest()
  }
}
