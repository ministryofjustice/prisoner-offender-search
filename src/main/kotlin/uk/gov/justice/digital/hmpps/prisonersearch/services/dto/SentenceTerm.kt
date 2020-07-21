package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class SentenceTerm (
  val bookingId: Long,
  val sentenceSequence: Int,
  val termSequence: Int,
  val startDate: LocalDate,
  val lifeSentence: Boolean,
  val sentenceTermCode: String,
  val lineSeq: Long,
  val sentenceStartDate: LocalDate,
  val caseId: String?,
  val consecutiveTo: Int?,
  val sentenceType: String,
  val sentenceTypeDescription: String?,
  val fineAmount: Double?,
  val years: Int?,
  val months: Int?,
  val weeks: Int?,
  val days: Int?
)