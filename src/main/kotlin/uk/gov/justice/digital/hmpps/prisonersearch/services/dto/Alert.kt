package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class Alert (
  val alertId: Long,
  val offenderNo: String?,
  val alertType: String,
  val alertTypeDescription: String?,
  val alertCode: String,
  val alertCodeDescription: String?,
  val comment: String?,
  val dateCreated: LocalDate,
  val dateExpires: LocalDate?,
  val expired : Boolean,
  val active : Boolean,
  val addedByFirstName: String?,
  val addedByLastName: String?,
  val expiredByFirstName: String?,
  val expiredByLastName: String?
)