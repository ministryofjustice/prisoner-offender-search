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
  val caseId: String? = null,
  val consecutiveTo: Int? = null,
  val sentenceType: String,
  val sentenceTypeDescription: String? = null,
  val fineAmount: Double? = null,
  val years: Int? = null,
  val months: Int? = null,
  val weeks: Int? = null,
  val days: Int? = null
)