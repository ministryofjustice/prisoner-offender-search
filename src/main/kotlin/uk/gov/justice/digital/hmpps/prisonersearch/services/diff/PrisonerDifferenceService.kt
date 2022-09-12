package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.ToStringStyle
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import kotlin.reflect.full.findAnnotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiffableProperty(val type: PropertyType)

enum class PropertyType {
  IDENTIFIERS, PERSONAL_DETAILS, STATUS, LOCATION, SENTENCE, RESTRICTED_PATIENT
}

data class Difference(val property: String, val propertyType: PropertyType, val oldValue: Any, val newValue: Any)

fun getDifferencesByPropertyType(prisoner: Prisoner, other: Prisoner): Map<PropertyType, List<Difference>> =
  getDiffResult(prisoner, other).let { diffResult ->
    propertiesByPropertyType.mapValues { properties ->
      val diffs = diffResult.diffs as List<Diff<Prisoner>>
      diffs.filter { diff -> properties.value.contains(diff.fieldName) }
        .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
    }
  }.filter { it.value.isNotEmpty() }

internal fun getDiffResult(prisoner: Prisoner, other: Prisoner): DiffResult<Prisoner> =
  DiffBuilder(prisoner, other, ToStringStyle.JSON_STYLE).apply {
    Prisoner::class.members
      .filter { it.findAnnotations<DiffableProperty>().isNotEmpty() }
      .forEach {
        append(it.name, it.call(prisoner), it.call(other))
      }
  }.build()

val propertiesByPropertyType: Map<PropertyType, List<String>> =
  Prisoner::class.members
    .filter { it.findAnnotations<DiffableProperty>().isNotEmpty() }
    .groupBy { it.findAnnotations<DiffableProperty>().first().type }
    .mapValues { it.value.map { property -> property.name } }

val propertyTypesByProperty: Map<String, PropertyType> =
  Prisoner::class.members
    .filter { it.findAnnotations<DiffableProperty>().isNotEmpty() }
    .associate { it.name to it.findAnnotations<DiffableProperty>().first().type }
