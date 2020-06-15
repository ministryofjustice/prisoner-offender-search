package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "localstack")
class JmsLocalStackConfig {
  @Bean("awsSqsClient")
  fun awsSqsClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                             @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsDlqClient")
  fun awsSqsDlqClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                     @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexASyncClient")
  fun awsSqsIndexASyncClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                       @Value("\${sqs.endpoint.region}") region: String): AmazonSQSAsync =
    AmazonSQSAsyncClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexClient")
  fun awsSqsIndexClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                  @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexDlqClient")
  fun awsSqsIndexDlqClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                          @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

}