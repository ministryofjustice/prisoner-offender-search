package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Search Criteria for Global Prisoner Search")
data class GlobalSearchCriteria(
  @Schema(
    description = "Prisoner identifier, one of prisoner number, book number, booking ID or PNC",
    example = "A1234AA"
  )
  val prisonerIdentifier: String?,
  @Schema(description = "First Name", example = "John")
  val firstName: String?,
  @Schema(description = "Last Name", example = "Smith")
  val lastName: String?,
  @Schema(
    description = "Gender, F - Female, M - Male, NK - Not Known / Not Recorded or NS - Not Specified (Indeterminate)",
    example = "M"
  )
  val gender: Gender?,
  @Schema(description = "Location, All or Inside or Outside", example = "IN")
  val location: String?,
  @Schema(description = "Date of birth", example = "1970-02-28")
  val dateOfBirth: LocalDate? = null,
  @Schema(description = "Include aliases in search", example = "false", required = false, defaultValue = "false")
  val includeAliases: Boolean = false
) {

  @Schema(hidden = true)
  fun isValid() = !(firstName.isNullOrBlank() && lastName.isNullOrBlank() && prisonerIdentifier.isNullOrBlank())
}

//offenderNo, lastName, firstName, gender, location, dateOfBirth, includeAliases


enum class Gender(val value: String?) {

  M("Male"),
  F("Female"),
  NK("known"),
  NS("specified"),
  ALL("ALL");
}
