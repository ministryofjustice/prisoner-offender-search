package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Search Criteria for all prisoner in a prison")

data class PrisonId(
  @Schema(description = "List of offenders at location", example = "`LEI`")
  val location: String?) {
  @Schema(hidden = true)
  fun isValid() = !(location.isNullOrBlank())
}

