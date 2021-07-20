package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Search Criteria for Prisoner Search")
data class RestrictedPatientSearchCriteria(
  @Schema(
    description = "Prisoner identifier, one of prisoner number, book number, booking ID or PNC",
    example = "A1234AA,"
  )
  val prisonerIdentifier: String?,
  @Schema(description = "First Name", example = "John")
  val firstName: String?,
  @Schema(description = "Last Name", example = "Smith")
  val lastName: String?,
) {
  @Schema(hidden = true)
  fun isEmpty() = firstName.isNullOrBlank() && lastName.isNullOrBlank() && prisonerIdentifier.isNullOrBlank()
}
