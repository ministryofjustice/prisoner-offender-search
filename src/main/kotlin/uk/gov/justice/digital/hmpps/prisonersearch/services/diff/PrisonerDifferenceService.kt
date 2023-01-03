package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.amazonaws.util.Base64
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.ToStringStyle
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.DigestUtils
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.Instant
import java.time.LocalDateTime
import kotlin.reflect.full.findAnnotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiffableProperty(val type: DiffCategory)

enum class DiffCategory {
  IDENTIFIERS, PERSONAL_DETAILS, ALERTS, STATUS, LOCATION, SENTENCE, RESTRICTED_PATIENT, INCENTIVE_LEVEL
}

data class Difference(val property: String, val categoryChanged: DiffCategory, val oldValue: Any?, val newValue: Any?)

typealias PrisonerDifferences = Map<DiffCategory, List<Difference>>

internal fun getDiffResult(prisoner: Prisoner, other: Prisoner): DiffResult<Prisoner> =
  DiffBuilder(prisoner, other, ToStringStyle.JSON_STYLE).apply {
    Prisoner::class.members
      .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
      .forEach { property -> append(property.name, property.call(prisoner), property.call(other)) }
  }.build()

@Service
class PrisonerDifferenceService(
  private val telemetryClient: TelemetryClient,
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val diffProperties: DiffProperties,
  private val prisonerEventHashRepository: PrisonerEventHashRepository,
  private val objectMapper: ObjectMapper,
  private val prisonerMovementsEventService: PrisonerMovementsEventService,
  private val alertsUpdatedEventService: AlertsUpdatedEventService,
) {

  internal val propertiesByDiffCategory: Map<DiffCategory, List<String>> =
    Prisoner::class.members
      .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
      .groupBy { property -> property.findAnnotations<DiffableProperty>().first().type }
      .mapValues { propertiesByDiffCategory -> propertiesByDiffCategory.value.map { property -> property.name } }

  internal val diffCategoriesByProperty: Map<String, DiffCategory> =
    Prisoner::class.members
      .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
      .associate { property -> property.name to property.findAnnotations<DiffableProperty>().first().type }

  @Transactional
  fun handleDifferences(previousPrisonerSnapshot: Prisoner?, offenderBooking: OffenderBooking, prisoner: Prisoner) {
    if (prisonerHasChanged(offenderBooking.offenderNo, prisoner)) {
      generateDiffEvent(previousPrisonerSnapshot, offenderBooking, prisoner)
      generateDiffTelemetry(previousPrisonerSnapshot, offenderBooking, prisoner)
      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking)
      alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)
    } else {
      raiseNoDifferencesTelemetry(offenderBooking.offenderNo, previousPrisonerSnapshot, prisoner)
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun prisonerHasChanged(nomsNumber: String, prisoner: Prisoner): Boolean =
    prisonerEventHashRepository.upsertPrisonerEventHashIfChanged(
      nomsNumber,
      generatePrisonerHash(prisoner),
      Instant.now()
    ) > 0

  private fun generatePrisonerHash(prisoner: Prisoner) =
    objectMapper.writeValueAsString(prisoner)
      .toByteArray()
      .let {
        Base64.encodeAsString(*DigestUtils.md5Digest(it))
      }

  internal fun generateDiffTelemetry(
    previousPrisonerSnapshot: Prisoner?,
    offenderBooking: OffenderBooking,
    prisoner: Prisoner
  ) {
    if (!diffProperties.telemetry) return

    kotlin.runCatching {
      previousPrisonerSnapshot?.also {
        val differences = getDifferencesByCategory(it, prisoner)
        if (differences.isEmpty()) {
          raiseNoDifferencesFoundTelemetry(offenderBooking.offenderNo)
        } else {
          raiseDifferencesTelemetry(offenderBooking.offenderNo, differences)
        }
      }
        ?: raiseCreatedTelemetry(offenderBooking.offenderNo)
    }.onFailure {
      PrisonerIndexService.log.error("Prisoner difference telemetry failed with error", it)
    }
  }

  internal fun generateDiffEvent(
    previousPrisonerSnapshot: Prisoner?,
    offenderBooking: OffenderBooking,
    prisoner: Prisoner
  ) {
    if (!diffProperties.events) return
    previousPrisonerSnapshot?.also {
      getDifferencesByCategory(it, prisoner)
        .takeIf { differences -> differences.isNotEmpty() }
        ?.also { differences ->
          domainEventEmitter.emitPrisonerDifferenceEvent(offenderBooking.offenderNo, differences)
        }
    }
      ?: domainEventEmitter.emitPrisonerCreatedEvent(offenderBooking.offenderNo)
  }

  internal fun getDifferencesByCategory(prisoner: Prisoner, other: Prisoner): PrisonerDifferences =
    getDiffResult(prisoner, other).let { diffResult ->
      propertiesByDiffCategory.mapValues { properties ->
        val diffs = diffResult.diffs as List<Diff<Prisoner>>
        diffs.filter { diff -> properties.value.contains(diff.fieldName) }
          .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
      }
    }.filter { differencesByCategory -> differencesByCategory.value.isNotEmpty() }

  private fun raiseDifferencesTelemetry(offenderNo: String, differences: PrisonerDifferences) =
    telemetryClient.trackEvent(
      "POSPrisonerUpdated",
      mapOf(
        "processedTime" to LocalDateTime.now().toString(),
        "nomsNumber" to offenderNo,
        "categoriesChanged" to differences.keys.map { it.name }.toList().sorted().toString(),
      ),
      null
    )

  private fun raiseNoDifferencesTelemetry(offenderNo: String, previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner) =
    telemetryClient.trackEvent(
      "POSPrisonerUpdatedNoChange",
      mapOf(
        "processedTime" to LocalDateTime.now().toString(),
        "nomsNumber" to offenderNo,
        "hasChanges" to (previousPrisonerSnapshot.asJson() != prisoner.asJson()).toString(),
      ),
      null
    )

  private fun Prisoner?.asJson(): String = this?.let { objectMapper.writeValueAsString(this) } ?: ""
  private fun raiseNoDifferencesFoundTelemetry(offenderNo: String) =
    telemetryClient.trackEvent(
      "POSPrisonerUpdatedNoChangesFound",
      mapOf(
        "processedTime" to LocalDateTime.now().toString(),
        "nomsNumber" to offenderNo,
      ),
      null
    )

  private fun raiseCreatedTelemetry(offenderNo: String) =
    telemetryClient.trackEvent("POSPrisonerCreated", mapOf("nomsNumber" to offenderNo), null)
}
