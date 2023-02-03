package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import io.swagger.v3.oas.annotations.media.Schema

data class PhysicalDetailRequest(
  @Schema(
    description = "List of prison codes to filter results by",
    example = "['LEI', 'MDI']",
    required = true,
  )
  val prisonIds: List<String>? = emptyList(),

  @Schema(
    description = "Minimum height of the prisoner in centimetres",
    example = "198",
  )
  val minHeight: Int? = null,

  @Schema(
    description = "Maximum height of the prisoner in centimetres",
    example = "198",
  )
  val maxHeight: Int? = null,

  @Schema(
    description = "Pagination options. Will default to the first page if omitted.",
    required = false,
  )
  val pagination: PaginationRequest = PaginationRequest(0, 10),
)
