package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.Health.Builder
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.services.health.DlqStatus.*
import uk.gov.justice.digital.hmpps.prisonersearch.services.health.QueueAttributes.*

@Component
@ConditionalOnExpression("{'aws', 'localstack', 'embedded-localstack'}.contains('\${sqs.provider}')")
class IndexQueueHealth(@Autowired @Qualifier("awsSqsIndexClient") private val awsSqsClient: AmazonSQS,
                  @Autowired @Qualifier("awsSqsIndexDlqClient") private val awsSqsDlqClient: AmazonSQS,
                  @Value("\${sqs.index.queue.name}") private val queueName: String,
                  @Value("\${sqs.index.dlq.name}") private val dlqName: String) : HealthIndicator {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun health(): Health {
    val queueAttributes = try {
      val url = awsSqsClient.getQueueUrl(queueName)
      awsSqsClient.getQueueAttributes(getQueueAttributesRequest(url))
    } catch (e: Exception) {
      log.error("Unable to retrieve queue attributes for queue '{}' due to exception:", queueName, e)
      return Builder().down().withException(e).build()
    }
    val details = mutableMapOf<String, Any?>(
        MESSAGES_ON_QUEUE.healthName to queueAttributes.attributes[MESSAGES_ON_QUEUE.awsName]?.toInt(),
        MESSAGES_IN_FLIGHT.healthName to queueAttributes.attributes[MESSAGES_IN_FLIGHT.awsName]?.toInt()
    )

    val health = Builder().up().withDetails(details).addDlqHealth(queueAttributes).build()

    log.info("Found health details for queue '{}': {}", queueName, health)
    return health
  }

  private fun Builder.addDlqHealth(mainQueueAttributes: GetQueueAttributesResult): Builder {
    if (!mainQueueAttributes.attributes.containsKey("RedrivePolicy")) {
      log.error("Queue '{}' is missing a RedrivePolicy attribute indicating it does not have a dead letter queue", queueName)
      return down().withDetail("dlqStatus", NOT_ATTACHED.description)
    }

    val dlqAttributes = try {
      val url = awsSqsDlqClient.getQueueUrl(dlqName)
      awsSqsDlqClient.getQueueAttributes(getQueueAttributesRequest(url))
    } catch (e: QueueDoesNotExistException) {
      log.error("Unable to retrieve dead letter queue URL for queue '{}' due to exception:", queueName, e)
      return down(e).withDetail("dlqStatus", NOT_FOUND.description)
    } catch (e: Exception) {
      log.error("Unable to retrieve dead letter queue attributes for queue '{}' due to exception:", queueName, e)
      return down(e).withDetail("dlqStatus", NOT_AVAILABLE.description)
    }

    return withDetail("dlqStatus", DlqStatus.UP.description)
        .withDetail(MESSAGES_ON_DLQ.healthName, dlqAttributes.attributes[MESSAGES_ON_DLQ.awsName]?.toInt())
  }

  private fun getQueueAttributesRequest(url: GetQueueUrlResult) =
      GetQueueAttributesRequest(url.queueUrl).withAttributeNames(QueueAttributeName.All)

}
