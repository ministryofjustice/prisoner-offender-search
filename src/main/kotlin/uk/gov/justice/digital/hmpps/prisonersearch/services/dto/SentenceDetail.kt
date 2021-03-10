package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class SentenceDetail(
  val sentenceExpiryDate: LocalDate? = null,
  val automaticReleaseDate: LocalDate? = null,
  val conditionalReleaseDate: LocalDate? = null,
  val nonParoleDate: LocalDate? = null,
  val postRecallReleaseDate: LocalDate? = null,
  val licenceExpiryDate: LocalDate? = null,
  val homeDetentionCurfewEligibilityDate: LocalDate? = null,
  val paroleEligibilityDate: LocalDate? = null,
  val homeDetentionCurfewActualDate: LocalDate? = null,
  val homeDetentionCurfewEndDate: LocalDate? = null,
  val actualParoleDate: LocalDate? = null,
  val releaseOnTemporaryLicenceDate: LocalDate? = null,
  val earlyRemovalSchemeEligibilityDate: LocalDate? = null,
  val earlyTermDate: LocalDate? = null,
  val midTermDate: LocalDate? = null,
  val lateTermDate: LocalDate? = null,
  val topupSupervisionExpiryDate: LocalDate? = null,
  val topupSupervisionStartDate: LocalDate? = null,
  val tariffDate: LocalDate? = null,
  val sentenceStartDate: LocalDate? = null,
  val additionalDaysAwarded: Int? = null,
  val automaticReleaseOverrideDate: LocalDate? = null,
  val conditionalReleaseOverrideDate: LocalDate? = null,
  val nonParoleOverrideDate: LocalDate? = null,
  val postRecallReleaseOverrideDate: LocalDate? = null,
  val nonDtoReleaseDate: LocalDate? = null,
  val nonDtoReleaseDateType: String? = null,
  val confirmedReleaseDate: LocalDate? = null,
  val releaseDate: LocalDate? = null
)
