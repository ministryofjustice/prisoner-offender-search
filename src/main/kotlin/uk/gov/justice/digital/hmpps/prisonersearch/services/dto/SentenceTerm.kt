package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class SentenceTerm (
  var bookingId: Long? = null,
  val sentenceSequence: Int? = null,
  val termSequence: Int? = null,
  val consecutiveTo: Int? = null,
  val sentenceType: String? = null,
  val sentenceTypeDescription: String? = null,
  val startDate: LocalDate? = null,
  val years: Int? = null,
  val months: Int? = null,
  val weeks: Int? = null,
  val days: Int? = null,
  val lifeSentence: Boolean? = null,
  val caseId: String? = null,
  val fineAmount: Double? = null,
  val sentenceTermCode: String? = null,
  val lineSeq: Long? = null,
  val sentenceStartDate: LocalDate? = null
)