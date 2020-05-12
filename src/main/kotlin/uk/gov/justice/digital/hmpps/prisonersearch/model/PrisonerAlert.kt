package uk.gov.justice.digital.hmpps.prisonersearch.model

data class PrisonerAlert (
  val alertId: Long,
  val alertType: String,
  val alertCode: String
)