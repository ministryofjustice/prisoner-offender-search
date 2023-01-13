package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexStatusService
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException

@Component
class IndexInfo(
  private val indexStatusService: IndexStatusService,
  private val prisonerIndexService: PrisonerIndexService,
  hmppsQueueService: HmppsQueueService,
) : InfoContributor {

  private val indexQueue = hmppsQueueService.findByQueueId("indexqueue") ?: throw MissingQueueException("HmppsQueue indexqueue not found")

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

  private fun safeQueueCount(): String = try {
    val queueAttributes = indexQueue.sqsClient.getQueueAttributes(
      GetQueueAttributesRequest.builder()
        .queueUrl(indexQueue.queueUrl)
        .attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES)
        .build()
    ).get().attributes()

    queueAttributes[APPROXIMATE_NUMBER_OF_MESSAGES] ?: "unknown"
  } catch (ex: Exception) {
    "error retrieving queue count: ${ex.message}"
  }
}
