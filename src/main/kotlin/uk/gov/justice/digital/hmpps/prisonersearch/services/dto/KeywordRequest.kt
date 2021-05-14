package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import io.swagger.v3.oas.annotations.media.Schema

internal val defaultPaginationRequest = PaginationRequest(0, 10)

data class KeywordRequest(
  @Schema(
    description = "Match where any of the keywords are present in any text field",
    example = "smith james john",
    required = false,
  )
  val orWords: String? = null,

  @Schema(
    description = "Match where all keywords are present in any text field",
    example = "smith james",
    required = false,
  )
  val andWords: String? = null,

  @Schema(
    description = "Filter results where any of these words are present in any text field",
    example = "jonas",
    required = false,
  )
  val notWords: String? = null,

  @Schema(
    description = "Match only prisoners where the full phrase is present in any text field",
    example = "John Smith",
    required = false,
  )
  val exactPhrase: String? = null,

  @Schema(
    description = "Fuzzy matching. Allow a one character difference in spelling in word lengths below five and two differences above.",
    example = "Smith will match Smyth",
    required = false,
  )
  val fuzzyMatch: Boolean? = false,

  @Schema(
    description = "List of prison codes to filter results",
    example = "['LEI', 'MDI']",
    required = true,
  )
  val prisonIds: List<String>? = emptyList(),

  @Schema(
    description = "Pagination options. Will default to the first page if omitted.",
    required = false,
  )
  val pagination: PaginationRequest = defaultPaginationRequest,
)
