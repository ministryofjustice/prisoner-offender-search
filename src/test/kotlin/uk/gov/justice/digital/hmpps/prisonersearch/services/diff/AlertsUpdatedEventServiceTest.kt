package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter

internal class AlertsUpdatedEventServiceTest {
  private val domainEventsEmitter = mock<HmppsDomainEventEmitter>()
  private val objectMapper = JsonMapper.builder()
    .findAndAddModules()
    .build()

  private val alertsUpdatedEventService = AlertsUpdatedEventService(domainEventsEmitter)

  @Test
  internal fun `will not emit anything if changes are not related to alerts`() {
    val previousPrisonerSnapshot = prisoner()
    val prisoner = prisoner().apply {
      this.firstName = "BOBBY"
    }

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verifyNoInteractions(domainEventsEmitter)
  }

  @Test
  internal fun `will emit event if alert added`() {
    val previousPrisonerSnapshot = prisoner()
    val prisoner = prisoner().apply {
      this.alerts = listOf(PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false))
    }

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitPrisonerAlertsUpdatedEvent(
      offenderNo = OFFENDER_NO,
      alertsAdded = setOf("XA"),
      alertsRemoved = setOf(),
    )
  }

  @Test
  internal fun `will emit event if alert removed`() {
    val previousPrisonerSnapshot = prisoner().apply {
      this.alerts = listOf(PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false))
    }
    val prisoner = prisoner()

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitPrisonerAlertsUpdatedEvent(
      offenderNo = OFFENDER_NO,
      alertsAdded = setOf(),
      alertsRemoved = setOf("XA"),
    )
  }

  @Test
  internal fun `will emit event if alerts both add and removed`() {
    val previousPrisonerSnapshot = prisoner().apply {
      this.alerts = listOf(
        PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "XT", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "AA", active = true, expired = false)
      )
    }
    val prisoner = prisoner().apply {
      this.alerts = listOf(
        PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "XK", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "BB", active = true, expired = false)
      )
    }

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitPrisonerAlertsUpdatedEvent(
      offenderNo = OFFENDER_NO,
      alertsAdded = setOf("XK", "BB"),
      alertsRemoved = setOf("XT", "AA"),
    )
  }

  private fun prisoner(): Prisoner =
    objectMapper.readValue(
      AlertsUpdatedEventService::class.java.getResource("/receive-state-changes/first-new-booking.json")!!.readText(),
      Prisoner::class.java
    )
}
