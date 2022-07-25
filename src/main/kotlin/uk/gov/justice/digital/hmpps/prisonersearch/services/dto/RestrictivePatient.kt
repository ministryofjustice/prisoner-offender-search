package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class RestrictivePatient(
  var supportingPrisonId: String?,
  val dischargedHospital: Agency?,
  val dischargeDate: LocalDate,
  val dischargeDetails: String?
)

data class Agency(
  val agencyId: String,
  val description: String? = null,
  val longDescription: String? = null,
  val agencyType: String,
  val active: Boolean
)

data class RestrictedPatientDto(
  val id: Long,
  val prisonerNumber: String,
  val hospitalLocation: Agency,
  val dischargeTime: LocalDateTime,
  val commentText: String? = null,
)
