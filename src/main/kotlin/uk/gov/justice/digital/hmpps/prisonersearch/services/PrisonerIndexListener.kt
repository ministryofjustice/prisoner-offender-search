package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.COMPARE
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.OFFENDER
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.OFFENDER_COMPARISON
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.OFFENDER_COMPARISON_LIST
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.OFFENDER_LIST
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexRequestType.REBUILD

@Service
class PrisonerIndexListener(
  private val prisonerIndexService: PrisonerIndexService,
  @Qualifier("gson") private val gson: Gson,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "indexqueue", containerFactory = "hmppsQueueContainerFactoryProxy", concurrency = "5")
  fun processIndexRequest(requestJson: String?, msg: javax.jms.Message) {
    log.debug(requestJson)
    try {
      val indexRequest = gson.fromJson(requestJson, PrisonerIndexRequest::class.java)
      log.debug("Received message request {}", indexRequest)

      when (indexRequest.requestType) {
        REBUILD -> {
          msg.acknowledge() // ack before processing
          prisonerIndexService.addIndexRequestToQueue()
        }
        COMPARE -> {
          msg.acknowledge() // ack before processing
          prisonerIndexService.addCompareRequestToQueue()
        }
        OFFENDER_LIST -> indexRequest.pageRequest?.let { prisonerIndexService.addOffendersToBeProcessed(it, OFFENDER) }
        OFFENDER -> indexRequest.prisonerNumber?.let { prisonerIndexService.indexPrisoner(it) }
        OFFENDER_COMPARISON_LIST -> indexRequest.pageRequest?.let { prisonerIndexService.addOffendersToBeProcessed(it, OFFENDER_COMPARISON) }
        OFFENDER_COMPARISON -> indexRequest.prisonerNumber?.let { prisonerIndexService.compareAndMaybeIndexPrisoner(it) }
        else -> log.warn("Unexpected Message {}", requestJson)
      }
      log.trace("Finished index message request {}", indexRequest)
    } catch (e: Exception) {
      log.error("processIndexRequest() Unexpected error for " + msg, e)
      telemetryClient.trackEvent(
        "POSProcessIndexRequestError",
        mapOf("requestPayload" to requestJson, "message" to e.message),
        null,
      )
      throw e
    }
  }
}
