package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import java.time.LocalDate

data class MatchRequest(
  @Schema(description = "Prisoner first name", example = "john") val firstName: String? = null,
  @field:NotBlank(message = "Last name is required")
  @Schema(description = "Prisoner last Name", example = "smith") val lastName: String?,
  @field:Past(message = "Date of birth must be in the past")
  @Schema(description = "Prisoner date of birth", example = "1996-02-10") val dateOfBirth: LocalDate? = null,
  @Schema(description = "Police National Computer (PNC) number", example = "2018/0123456X") val pncNumber: String? = null,
  @Schema(description = "Criminal Records Office (CRO) number", example = "SF80/655108T") val croNumber: String? = null,
  @Schema(description = "The Prisoner NOMIS Id (aka prison number/offender no in DPS)", example = "A1234AB") val nomsNumber: String? = null
)
