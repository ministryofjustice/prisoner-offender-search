package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.localstack.LocalStackContainer


@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
open class JmsEmbeddedLocalStackConfig(private val localStackContainer: LocalStackContainer) {

  @Bean
  fun awsSqsClient(): AmazonSQS = AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localStackContainer.defaultCredentialsProvider)
      .build()

  @Bean
  fun awsSqsDlqClient(): AmazonSQS = AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localStackContainer.defaultCredentialsProvider)
      .build()

  @Bean
  fun awsSqsIndexASyncClient(): AmazonSQSAsync = AmazonSQSAsyncClientBuilder.standard()
    .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
    .withCredentials(localStackContainer.defaultCredentialsProvider)
    .build()

  @Bean
  fun awsSqsIndexClient(): AmazonSQS = AmazonSQSClientBuilder.standard()
    .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
    .withCredentials(localStackContainer.defaultCredentialsProvider)
    .build()

  @Bean
  fun awsSqsIndexDlqClient(): AmazonSQS = AmazonSQSClientBuilder.standard()
    .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
    .withCredentials(localStackContainer.defaultCredentialsProvider)
    .build()
}
