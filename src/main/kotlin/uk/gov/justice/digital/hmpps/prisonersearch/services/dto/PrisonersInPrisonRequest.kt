package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class PrisonersInPrisonRequest(
  val term: String? = null,
  val alertCodes: List<String> = emptyList(),
  val pagination: PaginationRequest = PaginationRequest(0, 10),
  val fromDob: LocalDate? = null,
  val toDob: LocalDate? = null,
)
