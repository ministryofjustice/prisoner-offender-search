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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnExpression("{'localstack', 'embedded-localstack'}.contains('\${sqs.provider}')")
class ElasticSearchConfig {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
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
      log.info("Domain creation response from Amazon Elasticsearch Service: {}", createResponse.domainStatus)
    }
    return client
  }

}
