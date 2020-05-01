package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class SentenceDetail (
  val sentenceExpiryDate: LocalDate?,
  val automaticReleaseDate: LocalDate?,
  val conditionalReleaseDate: LocalDate?,
  val nonParoleDate: LocalDate?,
  val postRecallReleaseDate: LocalDate?,
  val licenceExpiryDate: LocalDate?,
  val homeDetentionCurfewEligibilityDate: LocalDate?,
  val paroleEligibilityDate: LocalDate?,
  val homeDetentionCurfewActualDate: LocalDate?,
  val actualParoleDate: LocalDate?,
  val releaseOnTemporaryLicenceDate: LocalDate?,
  val earlyRemovalSchemeEligibilityDate: LocalDate?,
  val earlyTermDate: LocalDate?,
  val midTermDate: LocalDate?,
  val lateTermDate: LocalDate?,
  val topupSupervisionExpiryDate: LocalDate?,
  val tariffDate: LocalDate?,
  val sentenceStartDate: LocalDate?,
  val additionalDaysAwarded: Int?,
  val automaticReleaseOverrideDate: LocalDate?,
  val conditionalReleaseOverrideDate: LocalDate?,
  val nonParoleOverrideDate: LocalDate?,
  val postRecallReleaseOverrideDate: LocalDate?,
  val nonDtoReleaseDate: LocalDate?,
  val nonDtoReleaseDateType: String?,
  val confirmedReleaseDate: LocalDate?,
  val releaseDate: LocalDate?
)