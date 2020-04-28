package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrisonerSyncService(
    private val telemetryClient: TelemetryClient,
    private val nomisService: NomisService,
    private val prisonerIndexService: PrisonerIndexService
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun externalMovement(message: ExternalPrisonerMovementMessage) {
        nomisService.getOffender(message.bookingId)?.let {
            prisonerIndexService.sync(it)
        }
    }

    fun offenderBookingChange(message: OffenderBookingChangedMessage) {
        nomisService.getOffender(message.bookingId)?.let {
            prisonerIndexService.sync(it)
        }
    }

    fun offenderChange(message: OffenderChangedMessage) {
        nomisService.getOffender(message.offenderIdDisplay)?.let {
            prisonerIndexService.sync(it)
        }
    }

}