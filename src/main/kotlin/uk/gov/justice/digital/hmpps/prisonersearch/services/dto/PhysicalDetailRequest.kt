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
    description = "Filter for the prisoners cell location. A block wing or cell can be specified. With prison id can be included or absent so HEI-3-1 and 3-1 are equivalent when the prison id is HEI",
    example = "3-1",
  )
  val cellLocationPrefix: String? = null,

  @Schema(
    description = "Minimum height of the prisoner in centimetres",
    example = "170",
  )
  val minHeight: Int? = null,

  @Schema(
    description = "Maximum height of the prisoner in centimetres",
    example = "198",
  )
  val maxHeight: Int? = null,

  @Schema(
    description = "Minimum weight of the prisoner in kilograms",
    example = "80",
  )
  val minWeight: Int? = null,

  @Schema(
    description = "Maximum weight of the prisoner in kilograms",
    example = "90",
  )
  val maxWeight: Int? = null,

  @Schema(
    description = "Pagination options. Will default to the first page if omitted.",
    required = false,
  )
  val pagination: PaginationRequest = PaginationRequest(0, 10),
)
