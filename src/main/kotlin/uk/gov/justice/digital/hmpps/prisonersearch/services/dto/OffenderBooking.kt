package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import java.time.LocalDate

data class OffenderBooking (
  val bookingId: Long?,
  val bookingNo: String?,
  val offenderNo: String,
  val firstName: String,
  val middleName: String?,
  val lastName: String,
  val aliases: List<Alias>?,
  val agencyId: String?,
  val activeFlag : Boolean,
  val inOutStatus: String?,
  val religion: String?,
  val language: String?,
  val alerts: List<Alert>?,
  val assignedLivingUnit: AssignedLivingUnit?,
  val facialImageId: Long?,
  val dateOfBirth: LocalDate,
  val age: Int?,
  val physicalAttributes: PhysicalAttributes?,
  val physicalCharacteristics: List<PhysicalCharacteristic>?,
  val profileInformation: List<ProfileInformation>?,
  val physicalMarks: List<PhysicalMark>?,
  val assessments: List<Assessment>?,
  val csra: String?,
  val categoryCode: String?,
  val birthPlace: String?,
  val birthCountryCode: String?,
  val identifiers: List<OffenderIdentifier>?,
  val sentenceDetail: SentenceDetail?,
  val offenceHistory: List<OffenceHistoryDetail>?,
  val status: String?,
  val legalStatus: String?,
  val imprisonmentStatus: String?,
  val personalCareNeeds : List<PersonalCareNeed>?
)