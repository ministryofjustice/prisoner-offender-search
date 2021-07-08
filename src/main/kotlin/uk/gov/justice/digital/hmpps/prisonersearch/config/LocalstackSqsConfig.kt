package uk.gov.justice.digital.hmpps.prisontoprobation.config

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.SubscribeRequest
import com.amazonaws.services.sqs.AmazonSQS
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.MissingQueueException

@Configuration
@ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
class LocalstackSqsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  @DependsOn("hmppsQueueService")
  fun awsSnsClient(
    hmppsSqsProperties: HmppsSqsProperties,
    hmppsSnsProperties: HmppsSnsProperties,
    @Qualifier("eventqueue-sqs-client") prisonEventSqsClient: AmazonSQS,
  ): AmazonSNS =
    with(hmppsSqsProperties) {
      val topicName = hmppsSnsProperties.eventTopic().topicName
      val queueName = queues["eventqueue"]?.queueName ?: throw MissingQueueException("Queue eventqueue has not been configured")
      localstackAmazonSNS(localstackUrl, region)
        .also { snsClient -> snsClient.createTopic(topicName) }
        .also { log.info("Created localstack sns topic with name $topicName") }
        .also {
          subscribeToTopic(
            it,
            localstackUrl,
            region,
            topicName,
            queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "OFFENDER-INSERTED", "OFFENDER-UPDATED", "ASSESSMENT-CHANGED", "OFFENDER_BOOKING-REASSIGNED", "OFFENDER_BOOKING-CHANGED", "OFFENDER_DETAILS-CHANGED", "BOOKING_NUMBER-CHANGED", "SENTENCE_DATES-CHANGED", "IMPRISONMENT_STATUS-CHANGED", "BED_ASSIGNMENT_HISTORY-INSERTED", "DATA_COMPLIANCE_DELETE-OFFENDER", "CONFIRMED_RELEASE_DATE-CHANGED", "OFFENDER_ALIAS-CHANGED", "OFFENDER_PROFILE_DETAILS-INSERTED", "OFFENDER_PROFILE_DETAILS-UPDATED"] }""")
          )
        }
    }

  private fun subscribeToTopic(
    awsSnsClient: AmazonSNS,
    localstackUrl: String,
    region: String,
    topicName: String,
    queueName: String,
    attributes: Map<String, String>
  ) =
    awsSnsClient.subscribe(
      SubscribeRequest()
        .withTopicArn(localstackTopicArn(region, topicName))
        .withProtocol("sqs")
        .withEndpoint("$localstackUrl/queue/$queueName")
        .withAttributes(attributes)
    )

  private fun localstackTopicArn(region: String, topicName: String) = "arn:aws:sns:$region:000000000000:$topicName"

  private fun localstackAmazonSNS(localstackUrl: String, region: String) =
    AmazonSNSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(localstackUrl, region))
      .build()
}
