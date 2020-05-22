package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.*

@Service
class PrisonerIndexListener(
    private val prisonerIndexService: PrisonerIndexService,
    @Qualifier("gson") private val gson : Gson
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${sqs.index.queue.name}", containerFactory = "jmsIndexListenerContainerFactory")
  fun processIndexRequest(requestJson: String?, msg : javax.jms.Message ) {
    log.debug(requestJson)
    val indexRequest = gson.fromJson(requestJson, PrisonerIndexRequest::class.java)
    log.debug("Received message request {}", indexRequest)

    when (indexRequest.requestType) {
      REBUILD -> {
        msg.acknowledge()  // ack before processing
        prisonerIndexService.addIndexRequestToQueue()
      }
      OFFENDER_LIST -> indexRequest.pageRequest?.let { prisonerIndexService.addOffendersToBeIndexed(it) }
      OFFENDER -> indexRequest.prisonerNumber?.let { prisonerIndexService.indexPrisoner(it) }
    }
  }

}
