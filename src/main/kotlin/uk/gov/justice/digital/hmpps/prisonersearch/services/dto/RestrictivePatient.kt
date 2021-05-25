package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class RestrictivePatient(
  var supportingPrison: Agency?,
  val dischargedHospital: Agency?,
  val dischargeDate: LocalDate,
  val dischargeDetails: String?
)

data class Agency(
  val agencyId: String,
  val description: String?,
  val longDescription: String?,
  val agencyType: String,
  val active: Boolean
)
