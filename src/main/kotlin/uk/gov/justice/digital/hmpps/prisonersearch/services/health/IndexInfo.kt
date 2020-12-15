package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexStatusService
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService

@Component
class IndexInfo(
  private val indexStatusService: IndexStatusService,
  private val prisonerIndexService: PrisonerIndexService,
  @Value("\${sqs.index.queue.name}") private val indexQueueName: String,
  @Qualifier("awsSqsIndexClient") private val indexAwsSqsClient: AmazonSQS
) : InfoContributor {

  override fun contribute(builder: Info.Builder) {
    val indexStatus = indexStatusService.getCurrentIndex()
    builder.withDetail("index-status", indexStatus)
    builder.withDetail(
      "index-size",
      mapOf(
        indexStatus.currentIndex.name to prisonerIndexService.countIndex(indexStatus.currentIndex),
        indexStatus.currentIndex.otherIndex().name to prisonerIndexService.countIndex(indexStatus.currentIndex.otherIndex())
      )
    )
    builder.withDetail("index-queue-backlog", safeQueueCount())
  }

  private fun safeQueueCount(): String {
    return try {
      val url = indexAwsSqsClient.getQueueUrl(indexQueueName)
      val queueAttributes = indexAwsSqsClient.getQueueAttributes(
        GetQueueAttributesRequest(url.queueUrl).withAttributeNames(
          QueueAttributeName.ApproximateNumberOfMessages
        )
      )
        .attributes

      queueAttributes["ApproximateNumberOfMessages"] ?: "unknown"
    } catch (ex: Exception) {
      "error retrieving queue count: ${ex.message}"
    }
  }
}
