package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class PersonalCareNeed(
  val problemType: String,
  val problemCode: String,
  val problemStatus: String?,
  val problemDescription: String?,
  val commentText: String?,
  val startDate: LocalDate?,
  val endDate: LocalDate?,
)
