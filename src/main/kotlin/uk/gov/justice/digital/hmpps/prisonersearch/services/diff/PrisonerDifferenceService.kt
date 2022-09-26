package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.ToStringStyle
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonersearch.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.Instant
import java.time.LocalDateTime
import java.util.Objects
import kotlin.reflect.full.findAnnotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiffableProperty(val type: DiffCategory)

enum class DiffCategory {
  IDENTIFIERS, PERSONAL_DETAILS, ALERTS, STATUS, LOCATION, SENTENCE, RESTRICTED_PATIENT
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
  private val prisonerEventHashRepository: PrisonerEventHashRepository
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
  fun handleDifferences(existingPrisoner: Prisoner?, offenderBooking: OffenderBooking, storedPrisoner: Prisoner) {
    if (prisonerHasChanged(offenderBooking.offenderNo, storedPrisoner)) {
      generateDiffEvent(existingPrisoner, offenderBooking, storedPrisoner)
      generateDiffTelemetry(existingPrisoner, offenderBooking, storedPrisoner)
    }
  }

  private fun prisonerHasChanged(nomsNumber: String, prisoner: Prisoner): Boolean =
    prisonerEventHashRepository.upsertPrisonerEventHashIfChanged(nomsNumber, Objects.hashCode(prisoner), Instant.now()) > 0

  internal fun generateDiffTelemetry(
    existingPrisoner: Prisoner?,
    offenderBooking: OffenderBooking,
    storedPrisoner: Prisoner
  ) {
    if (!diffProperties.telemetry) return

    kotlin.runCatching {
      existingPrisoner?.also {
        raiseDifferencesTelemetry(
          offenderBooking.offenderNo,
          getDifferencesByCategory(it, storedPrisoner),
        )
      }
        ?: raiseCreatedTelemetry(offenderBooking.offenderNo)
    }.onFailure {
      PrisonerIndexService.log.error("Prisoner difference telemetry failed with error", it)
    }
  }

  internal fun generateDiffEvent(
    existingPrisoner: Prisoner?,
    offenderBooking: OffenderBooking,
    storedPrisoner: Prisoner
  ) {
    if (!diffProperties.events) return
    existingPrisoner?.also { prisoner ->
      getDifferencesByCategory(prisoner, storedPrisoner)
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

  private fun raiseDifferencesTelemetry(
    offenderNo: String,
    differences: PrisonerDifferences,
  ) {
    differences.forEach { diffCategoryMap ->
      telemetryClient.trackEvent(
        "POSPrisonerUpdated",
        mapOf(
          "processedTime" to LocalDateTime.now().toString(),
          "nomsNumber" to offenderNo,
          "categoryChanged" to diffCategoryMap.key.name,
        ) + diffCategoryMap.value.associate { difference ->
          difference.property to """${difference.oldValue} -> ${difference.newValue}"""
        },
        null
      )
    }
  }

  private fun raiseCreatedTelemetry(offenderNo: String) =
    telemetryClient.trackEvent("POSPrisonerCreated", mapOf("nomsNumber" to offenderNo), null)
}
