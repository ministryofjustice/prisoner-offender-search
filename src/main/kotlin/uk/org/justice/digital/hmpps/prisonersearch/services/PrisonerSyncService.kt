package uk.org.justice.digital.hmpps.prisonersearch.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.org.justice.digital.hmpps.prisonersearch.model.translate

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
        log.debug("Offender Movement [booking ID {}]", message.bookingId)

        nomisService.getOffender(message.bookingId)?.let {
            prisonerIndexService.save(translate(it))
        }
    }

    fun offenderBookingChange(message: OffenderBookingChangedMessage) {
        log.debug("Offender Booking Change [booking ID {}]", message.bookingId)

        nomisService.getOffender(message.bookingId)?.let {
            prisonerIndexService.save(translate(it))
        }
    }

    fun offenderChange(message: OffenderChangedMessage) {
        log.debug("Offender Change [Noms ID {}]", message.offenderIdDisplay)

        nomisService.getOffender(message.offenderIdDisplay)?.let {
            prisonerIndexService.save(translate(it))
        }
    }



}