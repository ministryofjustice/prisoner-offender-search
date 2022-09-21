package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.ToStringStyle
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import java.time.LocalDateTime
import kotlin.reflect.full.findAnnotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiffableProperty(val type: DiffCategory)

enum class DiffCategory {
  IDENTIFIERS, PERSONAL_DETAILS, ALERTS, STATUS, LOCATION, SENTENCE, RESTRICTED_PATIENT
}

data class Difference(val property: String, val categoryChanged: DiffCategory, val oldValue: Any?, val newValue: Any?)

typealias PrisonerDifferences = Map<DiffCategory, List<Difference>>

fun getDifferencesByCategory(prisoner: Prisoner, other: Prisoner): PrisonerDifferences =
  getDiffResult(prisoner, other).let { diffResult ->
    propertiesByDiffCategory.mapValues { properties ->
      val diffs = diffResult.diffs as List<Diff<Prisoner>>
      diffs.filter { diff -> properties.value.contains(diff.fieldName) }
        .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
    }
  }.filter { differencesByCategory -> differencesByCategory.value.isNotEmpty() }

internal fun getDiffResult(prisoner: Prisoner, other: Prisoner): DiffResult<Prisoner> =
  DiffBuilder(prisoner, other, ToStringStyle.JSON_STYLE).apply {
    Prisoner::class.members
      .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
      .forEach { property -> append(property.name, property.call(prisoner), property.call(other)) }
  }.build()

val propertiesByDiffCategory: Map<DiffCategory, List<String>> =
  Prisoner::class.members
    .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
    .groupBy { property -> property.findAnnotations<DiffableProperty>().first().type }
    .mapValues { propertiesByDiffCategory -> propertiesByDiffCategory.value.map { property -> property.name } }

val diffCategoriesByProperty: Map<String, DiffCategory> =
  Prisoner::class.members
    .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
    .associate { property -> property.name to property.findAnnotations<DiffableProperty>().first().type }

fun raiseDifferencesTelemetry(
  offenderNo: String,
  differences: PrisonerDifferences,
  telemetryClient: TelemetryClient
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
