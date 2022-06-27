package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonersInPrisonRequest(
  @Schema(
    description = "Match where any of the keywords are present in key fields",
    example = "smith",
    required = false,
  )
  val term: String? = null,

  @Schema(
    description = "Pagination options. Will default to the first page if omitted.",
    required = false,
  )
  val pagination: PaginationRequest = PaginationRequest(0, 10),
)
