package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.destination.DynamicDestinationResolver
import javax.jms.Session

@Configuration
@EnableJms
class JmsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun jmsListenerContainerFactory(awsSqsClient: AmazonSQS): DefaultJmsListenerContainerFactory {
    val factory = DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(SQSConnectionFactory(ProviderConfiguration(), awsSqsClient))
    factory.setDestinationResolver(DynamicDestinationResolver())
    factory.setConcurrency("1-1")
    factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    factory.setErrorHandler { t: Throwable? -> log.error("Error caught in jms listener", t) }
    return factory
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun jmsIndexListenerContainerFactory(awsSqsIndexClient: AmazonSQS): DefaultJmsListenerContainerFactory {
    val factory = DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(SQSConnectionFactory(ProviderConfiguration(), awsSqsIndexClient))
    factory.setDestinationResolver(DynamicDestinationResolver())
    factory.setConcurrency("1-1")
    factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    factory.setErrorHandler { t: Throwable? -> log.error("Error caught in jms listener", t) }
    return factory
  }

  @Bean("queueUrl")
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun queueUrl(
    @Qualifier("awsSqsClient") awsSqsClient: AmazonSQS,
    @Value("\${sqs.queue.name}") queueName: String
  ): String {
    return awsSqsClient.getQueueUrl(queueName).queueUrl
  }

  @Bean("dlqUrl")
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun dlqUrl(
    @Qualifier("awsSqsDlqClient") awsSqsDlqClient: AmazonSQS,
    @Value("\${sqs.dlq.name}") dlqName: String
  ): String {
    return awsSqsDlqClient.getQueueUrl(dlqName).queueUrl
  }

  @Bean("indexQueueUrl")
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun indexQueueUrl(
    @Qualifier("awsSqsIndexASyncClient") awsSqsIndexASyncClient: AmazonSQSAsync,
    @Value("\${sqs.index.queue.name}") indexQueueName: String
  ): String {
    return awsSqsIndexASyncClient.getQueueUrl(indexQueueName).queueUrl
  }

  @Bean("indexDlqUrl")
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun indexDlqUrl(
    @Qualifier("awsSqsIndexDlqClient") awsSqsIndexDlqClient: AmazonSQS,
    @Value("\${sqs.index.dlq.name}") indexDlqName: String
  ): String {
    return awsSqsIndexDlqClient.getQueueUrl(indexDlqName).queueUrl
  }

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsClient(
    @Value("\${sqs.aws.access.key.id}") accessKey: String,
    @Value("\${sqs.aws.secret.access.key}") secretKey: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsDlqClient(
    @Value("\${sqs.aws.dlq.access.key.id}") accessKey: String,
    @Value("\${sqs.aws.dlq.secret.access.key}") secretKey: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsIndexASyncClient(
    @Value("\${sqs.index.aws.access.key.id}") accessKey: String,
    @Value("\${sqs.index.aws.secret.access.key}") secretKey: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQSAsync =
    AmazonSQSAsyncClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsIndexClient(
    @Value("\${sqs.index.aws.access.key.id}") accessKey: String,
    @Value("\${sqs.index.aws.secret.access.key}") secretKey: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsIndexDlqClient(
    @Value("\${sqs.index.aws.dlq.access.key.id}") accessKey: String,
    @Value("\${sqs.index.aws.dlq.secret.access.key}") secretKey: String,
    @Value("\${sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()
}
