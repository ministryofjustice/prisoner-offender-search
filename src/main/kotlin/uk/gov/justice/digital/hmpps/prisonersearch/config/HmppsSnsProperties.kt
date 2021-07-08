package uk.gov.justice.digital.hmpps.prisontoprobation.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

const val LOCALSTACK_ARN_PREFIX = "arn:aws:sns:eu-west-2:000000000000:"

@ConstructorBinding
@ConfigurationProperties(prefix = "hmpps.sns")
@ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
data class HmppsSnsProperties(val topics: Map<String, TopicConfig>) {
  data class TopicConfig(
    val topicArn: String = "",
    val topicAccessKeyId: String = "",
    val topicSecretAccessKey: String = "",
  ) {
    val topicName
      get() = if (topicArn.startsWith(LOCALSTACK_ARN_PREFIX)) topicArn.removePrefix(LOCALSTACK_ARN_PREFIX) else "We only provide a topic name for localstack"
  }
}

fun HmppsSnsProperties.eventTopic() = topics["eventTopic"] ?: throw MissingTopicException("eventTopic has not been loaded from configuration properties")
class MissingTopicException(message: String) : RuntimeException(message)
