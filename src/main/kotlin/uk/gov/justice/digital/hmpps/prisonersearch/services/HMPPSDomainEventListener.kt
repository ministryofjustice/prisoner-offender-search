package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service

@Service
class HMPPSDomainEventListener(
  private val prisonerSyncService: PrisonerSyncService,
  @Qualifier("gson") private val gson: Gson,
  private val telemetryClient: TelemetryClient
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "hmppsdomainqueue", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun processDomainEvent(requestJson: String?) {
    try {
      val (message, messageId, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
      val eventType = messageAttributes.eventType.Value
      log.debug("Received message {} type {}", messageId, eventType)

      when (eventType) {
        "incentives.iep-review.inserted", "incentives.iep-review.updated", "incentives.iep-review.deleted" ->
          prisonerSyncService.offenderIncentiveChange(fromJson(message))

        else -> log.warn("We received a message of event type {} which I really wasn't expecting", eventType)
      }
      log.trace("Finished event message request {}", message)
    } catch (e: Exception) {
      log.error("processDomainEvent() Unexpected error", e)
      telemetryClient.trackEvent(
        "POSProcessHMPPSDomainEventRequestError",
        mapOf("requestPayload" to requestJson, "message" to e.message),
        null
      )

      throw e
    }
  }

  private inline fun <reified T> fromJson(message: String): T {
    return gson.fromJson(message, T::class.java)
  }
}

data class IncentiveChangedMessage(
  val additionalInformation: IncentiveChangeAdditionalInformation,
  val eventType: String,
  val description: String,
)

data class IncentiveChangeAdditionalInformation(
  val nomsNumber: String,
  val id: Long,
)
