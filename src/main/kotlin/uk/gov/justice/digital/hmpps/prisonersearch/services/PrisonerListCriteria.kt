package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Size

@Schema(description = "Search Criteria for a list of prisoners")

data class PrisonerListCriteria(
  @Schema(description = "List of prisoner numbers to search by", example = "[\"A1234AA\"]")
  @NotEmpty
  @Size(min = 1, max = 1000)
  val prisonerNumbers: List<String>
) {
  @Schema(hidden = true)
  fun isValid() = prisonerNumbers.isNotEmpty() && prisonerNumbers.size <= 1000
}
