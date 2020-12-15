package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class IndexQueueHealth(
  @Autowired @Qualifier("awsSqsIndexClient") private val awsSqsClient: AmazonSQS,
  @Autowired @Qualifier("awsSqsIndexDlqClient") private val awsSqsDlqClient: AmazonSQS,
  @Value("\${sqs.index.queue.name}") private val queueName: String,
  @Value("\${sqs.index.dlq.name}") private val dlqName: String
) : QueueHealth(awsSqsClient, awsSqsDlqClient, queueName, dlqName)
