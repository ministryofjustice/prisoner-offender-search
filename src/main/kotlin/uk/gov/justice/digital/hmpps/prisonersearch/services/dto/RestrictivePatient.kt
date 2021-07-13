package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate
import java.time.LocalDateTime

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

data class RestrictedPatientDto(
  val id: Long,
  val prisonerNumber: String,
  val fromLocation: Agency,
  val hospitalLocation: Agency,
  val supportingPrison: Agency,
  val dischargeTime: LocalDateTime,
  val commentText: String? = null,
  val active: Boolean? = true,
)
