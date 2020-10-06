package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.localstack.LocalStackContainer


@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
class JmsEmbeddedLocalStackConfig(private val localStackContainer: LocalStackContainer) {

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


    @Bean("queueUrl")
    fun queueUrl(@Qualifier("awsSqsClient") awsSqsClient: AmazonSQS,
                 @Value("\${sqs.queue.name}") queueName: String,
                 @Value("\${sqs.dlq.name}") dlqName: String): String {
        return queueUrlWorkaroundTestcontainers(awsSqsClient, queueName, dlqName)
    }

    @Bean("indexQueueUrl")
    fun indexQueueUrl(@Qualifier("awsSqsIndexASyncClient") awsSqsIndexASyncClient: AmazonSQSAsync,
                      @Value("\${sqs.index.queue.name}") indexQueueName: String,
                      @Value("\${sqs.index.dlq.name}") indexDlqName: String): String {
        return queueUrlWorkaroundTestcontainers(awsSqsIndexASyncClient, indexQueueName, indexDlqName)
    }

    private fun queueUrlWorkaroundTestcontainers(awsSqsClient: AmazonSQS, queueName: String, dlqName: String): String {
        val queueUrl = awsSqsClient.getQueueUrl(queueName).queueUrl
        val dlqUrl = awsSqsClient.getQueueUrl(dlqName).queueUrl
        // This is necessary due to a bug in localstack when running in testcontainers that the redrive policy gets lost
        val dlqArn = awsSqsClient.getQueueAttributes(dlqUrl, listOf(QueueAttributeName.QueueArn.toString()))

        // the queue should already be created by the setup script - but should reset the redrive policy
        awsSqsClient.createQueue(
            CreateQueueRequest(queueName).withAttributes(
                mapOf(
                    QueueAttributeName.RedrivePolicy.toString() to
                            """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"5"}""")
            ))

        return queueUrl
    }

}
