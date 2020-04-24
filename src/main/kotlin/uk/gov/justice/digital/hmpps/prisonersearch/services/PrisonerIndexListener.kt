package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.*


@Service
open class PrisonerIndexListener(
    private val prisonerSyncService: PrisonerSyncService,
    private val prisonerIndexService: PrisonerIndexService,
    @Qualifier("gson") private val gson : Gson
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${sqs.index.queue.name}")
  fun processIndexRequest(requestJson: String?) {
    log.debug(requestJson)
    val (requestType, indexData) = gson.fromJson(requestJson, IndexRequest::class.java)
    log.debug("Received message request {} {}", requestType, indexData)

    when (requestType) {
      REBUILD -> prisonerIndexService.indexAll()
      PRISON -> indexData?.let { prisonerIndexService.indexActivePrisonersInPrison(it) }
      OFFENDER -> indexData?.let { prisonerSyncService.syncPrisoner(it) }
    }
  }

}
