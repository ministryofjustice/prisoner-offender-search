package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Search Criteria for Prisoner Search")
data class SearchCriteria(
  @Schema(
    description = "Prisoner identifier, one of pryes but you don't need to isoner number, book number, booking ID or PNC",
    example = "A1234AA,"
  )
  val prisonerIdentifier: String?,
  @Schema(description = "First Name", example = "John")
  val firstName: String?,
  @Schema(description = "Last Name", example = "Smith")
  val lastName: String?,
  @Schema(description = "Prison Id, Prison Id or OUT or TRN", example = "MDI")
  val prisonId: String? = null,
  @Schema(description = "Include aliases in search", example = "false", required = false, defaultValue = "false")
  val includeAliases: Boolean = false
) {
  @Schema(hidden = true)
  fun isValid() = !(firstName.isNullOrBlank() && lastName.isNullOrBlank() && prisonerIdentifier.isNullOrBlank())
}
