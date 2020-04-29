package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.destination.DynamicDestinationResolver
import javax.jms.Session

@Configuration
@EnableJms
open class JmsConfig {
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

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsClient(@Value("\${sqs.aws.access.key.id}") accessKey: String,
                        @Value("\${sqs.aws.secret.access.key}") secretKey: String,
                        @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
      AmazonSQSClientBuilder.standard()
          .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
          .withRegion(region)
          .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsDlqClient(@Value("\${sqs.aws.dlq.access.key.id}") accessKey: String,
                           @Value("\${sqs.aws.dlq.secret.access.key}") secretKey: String,
                           @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
      AmazonSQSClientBuilder.standard()
          .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
          .withRegion(region)
          .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsIndexASyncClient(@Value("\${sqs.index.aws.access.key.id}") accessKey: String,
                        @Value("\${sqs.index.aws.secret.access.key}") secretKey: String,
                        @Value("\${sqs.endpoint.region}") region: String): AmazonSQSAsync =
    AmazonSQSAsyncClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "aws")
  fun awsSqsIndexDlqClient(@Value("\${sqs.index.aws.dlq.access.key.id}") accessKey: String,
                                @Value("\${sqs.index.aws.dlq.secret.access.key}") secretKey: String,
                                @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean("awsSqsClient")
  @ConditionalOnExpression("{'aws', 'full-localstack', 'localstack'}.contains('\${sqs.provider}')")
  fun awsSqsClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                  @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
      AmazonSQSClientBuilder.standard()
          .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
          .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
          .build()

  @Bean("awsSqsDlqClient")
  @ConditionalOnExpression("{'aws', 'full-localstack', 'localstack'}.contains('\${sqs.provider}')")
  open fun awsSqsDlqClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                     @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
      AmazonSQSClientBuilder.standard()
          .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
          .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
          .build()

  @Bean("awsSqsIndexASyncClient")
  @ConditionalOnExpression("{'aws', 'full-localstack', 'localstack'}.contains('\${sqs.provider}')")
  fun awsSqsIndexASyncClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                             @Value("\${sqs.endpoint.region}") region: String): AmazonSQSAsync =
    AmazonSQSAsyncClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexClient")
  @ConditionalOnExpression("{'aws', 'full-localstack', 'localstack'}.contains('\${sqs.provider}')")
  fun awsSqsIndexClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                       @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsIndexDlqClient")
  @ConditionalOnExpression("{'aws', 'full-localstack', 'localstack'}.contains('\${sqs.provider}')")
  open fun awsSqsIndexDlqClientLocalstack(@Value("\${sqs.endpoint.url}") serviceEndpoint: String,
                                     @Value("\${sqs.endpoint.region}") region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("queueUrl")
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "localstack")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun queueUrl(@Qualifier("awsSqsClient") @Autowired awsSqsClient: AmazonSQS,
                    @Value("\${sqs.queue.name}") queueName: String,
                    @Value("\${sqs.dlq.name}") dlqName: String): String {
    return createQueue(awsSqsClient, queueName, dlqName)
  }

  @Bean("indexQueueUrl")
  @ConditionalOnExpression("{'aws', 'full-localstack'}.contains('\${sqs.provider}')")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun indexQueueUrl(@Qualifier("awsSqsIndexASyncClient") @Autowired awsSqsIndexASyncClient: AmazonSQSAsync,
                    @Value("\${sqs.index.queue.name}") indexQueueName: String,
                    @Value("\${sqs.index.dlq.name}") indexDlqName: String): String {
    return awsSqsIndexASyncClient.getQueueUrl(indexQueueName).queueUrl
  }

  @Bean("indexQueueUrl")
  @ConditionalOnProperty(name = ["sqs.provider"], havingValue = "localstack")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun indexQueueUrlLocalstack(@Qualifier("awsSqsIndexASyncClient") @Autowired awsSqsIndexASyncClient: AmazonSQSAsync,
                    @Value("\${sqs.index.queue.name}") indexQueueName: String,
                    @Value("\${sqs.index.dlq.name}") indexDlqName: String): String {
    return createQueue(awsSqsIndexASyncClient, indexQueueName, indexDlqName)
  }

  private fun createQueue(awsSqsClient: AmazonSQS, queueName: String, dlqName: String): String {
    log.info("**** Creating Queue {} and DLQ {}", queueName, dlqName)
    val result = awsSqsClient.createQueue(CreateQueueRequest(dlqName))
    val dlqArn = awsSqsClient.getQueueAttributes(result.queueUrl, listOf(QueueAttributeName.QueueArn.toString()))
    awsSqsClient.createQueue(CreateQueueRequest(queueName).withAttributes(
      mapOf(QueueAttributeName.RedrivePolicy.toString() to
          """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"3"}""")
    ))
    return awsSqsClient.getQueueUrl(queueName).queueUrl
  }
}
