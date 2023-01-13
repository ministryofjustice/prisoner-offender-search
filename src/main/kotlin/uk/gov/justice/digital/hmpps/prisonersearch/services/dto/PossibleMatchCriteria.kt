package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Past
import java.time.LocalDate

@Schema(description = "Search Criteria for possible match")
data class PossibleMatchCriteria(
  @Schema(description = "Prisoner first name", example = "john") val firstName: String? = null,
  @Schema(description = "Prisoner last Name", example = "smith") val lastName: String? = null,
  @field:Past(message = "Date of birth must be in the past")
  @Schema(description = "Prisoner date of birth", example = "1996-02-10") val dateOfBirth: LocalDate? = null,
  @Schema(description = "Police National Computer (PNC) number (This will match both long and short PNC formats)", example = "2018/0123456X") val pncNumber: String? = null,
  @Schema(description = "The Prisoner NOMIS Id (aka prison number/offender no in DPS)", example = "A1234AB") val nomsNumber: String? = null
) {
  @Schema(hidden = true)
  fun isValid() = !(nomsNumber.isNullOrBlank()) || !(pncNumber.isNullOrBlank()) || !(lastName.isNullOrBlank() && dateOfBirth == null)
}
