package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter

@Service
class AlertsUpdatedEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner
  ) {
    val previousAlerts = previousPrisonerSnapshot?.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alerts = prisoner.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alertsAdded = alerts - previousAlerts
    val alertsRemoved = previousAlerts - alerts

    if (alertsAdded.isNotEmpty() || alertsRemoved.isNotEmpty()) {
      domainEventEmitter.emitPrisonerAlertsUpdatedEvent(prisoner.prisonerNumber!!, prisoner.bookingId, alertsAdded, alertsRemoved)
    }
  }
}
