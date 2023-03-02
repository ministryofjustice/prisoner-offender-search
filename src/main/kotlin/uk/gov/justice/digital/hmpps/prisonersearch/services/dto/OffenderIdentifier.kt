package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class OffenderIdentifier(
  val type: String,
  val value: String,
  val issuedAuthorityText: String?,
  val issuedDate: LocalDate?,
  val whenCreated: LocalDateTime,
)
