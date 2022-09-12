package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.microsoft.applicationinsights.TelemetryClient
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
  }.filter { differencesByPropertyType -> differencesByPropertyType.value.isNotEmpty() }

internal fun getDiffResult(prisoner: Prisoner, other: Prisoner): DiffResult<Prisoner> =
  DiffBuilder(prisoner, other, ToStringStyle.JSON_STYLE).apply {
    Prisoner::class.members
      .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
      .forEach { property -> append(property.name, property.call(prisoner), property.call(other)) }
  }.build()

val propertiesByPropertyType: Map<PropertyType, List<String>> =
  Prisoner::class.members
    .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
    .groupBy { property -> property.findAnnotations<DiffableProperty>().first().type }
    .mapValues { propertiesByPropertyType -> propertiesByPropertyType.value.map { property -> property.name } }

val propertyTypesByProperty: Map<String, PropertyType> =
  Prisoner::class.members
    .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
    .associate { property -> property.name to property.findAnnotations<DiffableProperty>().first().type }

fun raiseDifferencesTelemetry(
  offenderNo: String,
  bookingNo: String?,
  differences: Map<PropertyType, List<Difference>>,
  telemetryClient: TelemetryClient
) {
  differences.forEach { propertyTypeMap ->
    telemetryClient.trackEvent(
      "POSPrisonerUpdated",
      mapOf(
        "offenderNumber" to offenderNo,
        "bookingNumber" to bookingNo,
        "propertyTypes" to propertyTypeMap.key.name,
      ) + propertyTypeMap.value.associate { difference ->
        difference.property to """${difference.oldValue} -> ${difference.newValue}"""
      },
      null
    )
  }
}
