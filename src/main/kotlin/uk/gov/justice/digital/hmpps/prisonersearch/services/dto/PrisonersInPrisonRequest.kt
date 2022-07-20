package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

data class PrisonersInPrisonRequest(
  val term: String? = null,

  val alertCodes: List<String> = emptyList(),

  val pagination: PaginationRequest = PaginationRequest(0, 10),
)
