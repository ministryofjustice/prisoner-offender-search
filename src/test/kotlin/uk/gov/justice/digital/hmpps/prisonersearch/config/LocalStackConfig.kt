package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer


@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
class LocalStackConfig {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun elasticSearchContainer() : ElasticsearchContainer {
    return ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:6.7.2")
  }

  @Bean
  fun localStackContainer(elasticsearchContainer: ElasticsearchContainer, applicationContext : ConfigurableApplicationContext): LocalStackContainer {
    log.info("Starting elasticsearch...")
    elasticsearchContainer.start()
    val elasticSearchPort = elasticsearchContainer.getMappedPort(9200)
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, "elasticsearch.port=$elasticSearchPort")
    log.info("Started elasticsearch on port {}", elasticSearchPort)

    log.info("Starting localstack...")
    val localStackContainer: LocalStackContainer = LocalStackContainer()
        .withServices(LocalStackContainer.Service.SQS)
        .withEnv("HOSTNAME_EXTERNAL", "localhost")

    localStackContainer.start()
    log.info("Started localstack.")
    return localStackContainer
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun queueUrl(@Autowired awsSqsClient: AmazonSQS,
               @Value("\${sqs.queue.name}") queueName: String,
               @Value("\${sqs.dlq.name}") dlqName: String): String {
    val result = awsSqsClient.createQueue(CreateQueueRequest(dlqName))
    val dlqArn = awsSqsClient.getQueueAttributes(result.queueUrl, listOf(QueueAttributeName.QueueArn.toString()))
    awsSqsClient.createQueue(CreateQueueRequest(queueName).withAttributes(
        mapOf(QueueAttributeName.RedrivePolicy.toString() to
            """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"3"}""")
    ))
    return awsSqsClient.getQueueUrl(queueName).queueUrl
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun indexQueueUrl(@Autowired awsSqsIndexASyncClient: AmazonSQSAsync,
               @Value("\${sqs.index.queue.name}") queueName: String,
               @Value("\${sqs.index.dlq.name}") dlqName: String): String {
    val result = awsSqsIndexASyncClient.createQueue(CreateQueueRequest(dlqName))
    val dlqArn = awsSqsIndexASyncClient.getQueueAttributes(result.queueUrl, listOf(QueueAttributeName.QueueArn.toString()))
    awsSqsIndexASyncClient.createQueue(CreateQueueRequest(queueName).withAttributes(
      mapOf(QueueAttributeName.RedrivePolicy.toString() to
          """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"3"}""")
    ))
    return awsSqsIndexASyncClient.getQueueUrl(queueName).queueUrl
  }
}
