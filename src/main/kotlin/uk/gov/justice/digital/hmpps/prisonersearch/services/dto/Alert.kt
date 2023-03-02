package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class Alert(
  val alertId: Long,
  val offenderNo: String? = null,
  val alertType: String,
  val alertTypeDescription: String? = null,
  val alertCode: String,
  val alertCodeDescription: String? = null,
  val comment: String? = null,
  val dateCreated: LocalDate,
  val dateExpires: LocalDate? = null,
  val expired: Boolean,
  val active: Boolean,
  val addedByFirstName: String? = null,
  val addedByLastName: String? = null,
  val expiredByFirstName: String? = null,
  val expiredByLastName: String? = null,
)
