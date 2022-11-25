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
    val previousAlertList = previousPrisonerSnapshot?.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alertList = prisoner.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alertsAdded = alertList - previousAlertList
    val alertsRemoved = previousAlertList - alertList

    if (alertsAdded.isNotEmpty() || alertsRemoved.isNotEmpty()) {
      domainEventEmitter.emitPrisonerAlertsUpdatedEvent(prisoner.prisonerNumber!!, alertsAdded, alertsRemoved)
    }
  }
}
