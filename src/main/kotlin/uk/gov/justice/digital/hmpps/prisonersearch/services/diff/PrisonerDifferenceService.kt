package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.ToStringStyle
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import kotlin.reflect.full.findAnnotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiffableType(val type: DiffType)

enum class DiffType {
  IDENTIFIERS, PERSONAL_DETAILS, STATUS, LOCATION, SENTENCE, RESTRICTED_PATIENT
}

data class Difference(val property: String, val diffType: DiffType, val oldValue: Any, val newValue: Any)

fun getDifferencesByType(prisoner: Prisoner, other: Prisoner): Map<DiffType, List<Difference>> =
  getDiff(prisoner, other).let { diffResult ->
    propertiesByDiffType.mapValues { properties ->
      val diffs = diffResult.diffs as List<Diff<Prisoner>>
      diffs.filter { diff -> properties.value.contains(diff.fieldName) }
        .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
    }
  }.filter { it.value.isNotEmpty() }

internal fun getDiff(prisoner: Prisoner, other: Prisoner): DiffResult<Prisoner> =
  DiffBuilder(prisoner, other, ToStringStyle.JSON_STYLE).apply {
    Prisoner::class.members
      .filter { it.findAnnotations<DiffableType>().isNotEmpty() }
      .forEach {
        append(it.name, it.call(prisoner), it.call(other))
      }
  }.build()

val propertiesByDiffType: Map<DiffType, List<String>> =
  Prisoner::class.members
    .filter { it.findAnnotations<DiffableType>().isNotEmpty() }
    .groupBy { it.findAnnotations<DiffableType>().first().type }
    .mapValues { it.value.map { property -> property.name } }

val diffTypesByProperty: Map<String, DiffType> =
  Prisoner::class.members
    .filter { it.findAnnotations<DiffableType>().isNotEmpty() }
    .associate { it.name to it.findAnnotations<DiffableType>().first().type }
