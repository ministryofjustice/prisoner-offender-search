package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "localstack")
class JmsLocalStackConfig(private val hmppsQueueService: HmppsQueueService) {

  @Bean("awsSqsClient")
  fun awsSqsClientLocalstack(
    @Value("\${sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${sqs.endpoint.region}") region: String,
    @Value("\${sqs.queue.name}") queueName: String,
    awsSqsDlqClient: AmazonSQS,
    @Value("\${sqs.dlq.name}") dlqName: String,
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()
      .also {
        hmppsQueueService.registerHmppsQueue(
          HmppsQueue(it, queueName, awsSqsDlqClient, dlqName)
        )
      }

  @Bean("awsSqsDlqClient")
  fun awsSqsDlqClientLocalstack(
    @Value("\${sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexASyncClient")
  fun awsSqsIndexASyncClientLocalstack(
    @Value("\${sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQSAsync =
    AmazonSQSAsyncClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexClient")
  fun awsSqsIndexClientLocalstack(
    @Value("\${sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${sqs.endpoint.region}") region: String,
    @Value("\${sqs.index.queue.name}") queueName: String,
    awsSqsIndexDlqClient: AmazonSQS,
    @Value("\${sqs.index.dlq.name}") dlqName: String,
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()
      .also {
        hmppsQueueService.registerHmppsQueue(
          HmppsQueue(it, queueName, awsSqsIndexDlqClient, dlqName)
        )
      }

  @Bean("awsSqsIndexDlqClient")
  fun awsSqsIndexDlqClientLocalstack(
    @Value("\${sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexDlqASyncClient")
  fun awsSqsIndexDlqASyncClientLocalstack(
    @Value("\${sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQSAsync =
    AmazonSQSAsyncClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("queueUrl")
  fun queueUrl(
    @Qualifier("awsSqsClient") awsSqsClient: AmazonSQS,
    @Value("\${sqs.queue.name}") queueName: String
  ): String {
    return awsSqsClient.getQueueUrl(queueName).queueUrl
  }

  @Bean("dlqUrl")
  fun dlqUrl(
    @Qualifier("awsSqsDlqClient") awsSqsDlqClient: AmazonSQS,
    @Value("\${sqs.dlq.name}") dlqName: String
  ): String {
    return awsSqsDlqClient.getQueueUrl(dlqName).queueUrl
  }

  @Bean("indexQueueUrl")
  fun indexQueueUrl(
    @Qualifier("awsSqsIndexASyncClient") awsSqsIndexASyncClient: AmazonSQSAsync,
    @Value("\${sqs.index.queue.name}") indexQueueName: String
  ): String {
    return awsSqsIndexASyncClient.getQueueUrl(indexQueueName).queueUrl
  }

  @Bean("indexDlqUrl")
  fun indexDlqUrl(
    @Qualifier("awsSqsIndexDlqClient") awsSqsIndexDlqClient: AmazonSQS,
    @Value("\${sqs.index.dlq.name}") indexDlqName: String
  ): String {
    return awsSqsIndexDlqClient.getQueueUrl(indexDlqName).queueUrl
  }
}
