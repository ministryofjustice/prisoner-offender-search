package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

/**
 * Assessment
 */
data class Assessment (
  val classificationCode: String,
  val classification: String,
  val assessmentCode: String,
  val assessmentDescription: String?,
  val cellSharingAlertFlag : Boolean,
  val assessmentDate: LocalDate?,
  val nextReviewDate: LocalDate?,
  val approvalDate: LocalDate?,
  val assessmentAgencyId: String?,
  val assessmentStatus: String?,
  val assessmentSeq: Int?
)