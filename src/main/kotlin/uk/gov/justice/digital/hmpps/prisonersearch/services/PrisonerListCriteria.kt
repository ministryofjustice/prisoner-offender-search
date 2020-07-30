package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Search Criteria for a list of prisoners")

data class PrisonerListCriteria(
  @Schema(description = "List of prisoner numbers to search by", example = "[\"A1234AA\"]")
  val prisonerNumbers: List<String>
) {
  fun isValid() = prisonerNumbers.isNotEmpty() && prisonerNumbers.size <= 200
}
