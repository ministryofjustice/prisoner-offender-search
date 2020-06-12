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
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.elasticsearch.ElasticsearchContainer


@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
class LocalStackConfig {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun localStackContainer(applicationContext : ConfigurableApplicationContext): LocalStackContainer {
    log.info("Starting elasticsearch...")
    val elasticsearchContainer = ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.6.1")
    elasticsearchContainer
        .withEnv("HOSTNAME_EXTERNAL", "localhost")
        .withClasspathResourceMapping("/localstack/setup-es.sh","/docker-entrypoint-initaws.d/setup-es.sh", BindMode.READ_WRITE)
        .withExposedPorts(9200,4578)
        .start()

    val elasticSearchPort = elasticsearchContainer.getMappedPort(9200)
    elasticsearchContainer.setCommand()
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, "elasticsearch.port=$elasticSearchPort")
    log.info("Started elasticsearch on port {}", elasticSearchPort)

    log.info("Starting localstack...")
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    val localStackContainer: LocalStackContainer = LocalStackContainer("0.11.2")
        .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
        .withClasspathResourceMapping("/localstack/setup-sns.sh","/docker-entrypoint-initaws.d/setup-sns.sh", BindMode.READ_WRITE)
        .withEnv("HOSTNAME_EXTERNAL", "localhost")
        .withEnv("DEFAULT_REGION", "eu-west-2")
        .waitingFor(
            Wait.forLogMessage(".*All Ready.*", 1)
        )

    log.info("Started localstack.")

    localStackContainer.start()
    localStackContainer.followOutput(logConsumer)
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
