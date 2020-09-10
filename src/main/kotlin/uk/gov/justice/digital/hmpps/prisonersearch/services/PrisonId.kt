package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Search Criteria for all prisoners at a prison")

data class PrisonId(
  @Schema(description = "Prison Id, Prison Id or OUT or TRN", example = "MDI")
  val prisonId: String?) {
  @Schema(hidden = true)
  fun isValid() = !(prisonId.isNullOrBlank())
}

