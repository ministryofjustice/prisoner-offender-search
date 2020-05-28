package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.elasticsearch.AWSElasticsearch
import com.amazonaws.services.elasticsearch.AWSElasticsearchClientBuilder
import com.amazonaws.services.elasticsearch.model.CreateElasticsearchDomainRequest
import com.amazonaws.services.elasticsearch.model.CreateElasticsearchDomainResult
import com.amazonaws.services.elasticsearch.model.DomainInfo
import com.amazonaws.services.elasticsearch.model.ListDomainNamesRequest
import org.elasticsearch.client.RestHighLevelClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.RestClients
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories


@Configuration
@EnableElasticsearchRepositories(basePackages = ["uk.gov.justice.digital.hmpps.prisonersearch.repository"])
class ElasticSearchConfiguration : AbstractElasticsearchConfiguration() {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Value("\${elasticsearch.port}")
  private val port = 0

  @Value("\${elasticsearch.host}")
  private val host: String? = null

  @Bean("elasticSearchClient")
  override fun elasticsearchClient(): RestHighLevelClient {
    return RestClients.create(ClientConfiguration.builder().connectedTo("$host:$port").build()).rest()
  }

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "localstack")
  fun awsElasticssearchLocalstack(
    @Value("\${es.endpoint.url}") serviceEndpoint: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AWSElasticsearch {
    val domainName = "es1"

    val client = AWSElasticsearchClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

    val listDomainNames = client.listDomainNames(ListDomainNamesRequest())

    if (!listDomainNames.domainNames.contains(DomainInfo().withDomainName(domainName))) {
      val createRequest = CreateElasticsearchDomainRequest()
        .withDomainName(domainName)
        .withElasticsearchVersion("7.1")

      log.debug("Sending domain creation request...")
      val createResponse: CreateElasticsearchDomainResult = client.createElasticsearchDomain(createRequest)
      log.info(
        "Domain creation response from Amazon Elasticsearch Service: {}",
        createResponse.domainStatus.toString()
      )
    }
    return client
  }
}

