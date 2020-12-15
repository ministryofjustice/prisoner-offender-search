package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class OffenderIdentifier(
  val type: String,
  val value: String,
  val issuedAuthorityText: String?,
  val issuedDate: LocalDate?
)
