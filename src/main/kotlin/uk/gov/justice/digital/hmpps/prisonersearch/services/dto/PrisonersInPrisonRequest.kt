package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import org.springframework.data.domain.Sort
import java.time.LocalDate

data class PrisonersInPrisonRequest(
  val term: String? = null,
  val alertCodes: List<String> = emptyList(),
  val pagination: PaginationRequest = PaginationRequest(0, 10),
  val fromDob: LocalDate? = null,
  val toDob: LocalDate? = null,
  val cellLocationPrefix: String? = null,
  val incentiveLevelCode: String? = null,
  val sort: Sort = Sort.by(Sort.Direction.ASC, "lastName", "firstName", "prisonNumber"),
)
