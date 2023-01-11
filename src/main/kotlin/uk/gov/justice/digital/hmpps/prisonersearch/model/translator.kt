package uk.gov.justice.digital.hmpps.prisonersearch.model

import uk.gov.justice.digital.hmpps.prisonersearch.services.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.services.canonicalPNCNumberLong
import uk.gov.justice.digital.hmpps.prisonersearch.services.canonicalPNCNumberShort
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictivePatient

fun PrisonerA(ob: OffenderBooking, incentiveLevel: IncentiveLevel?, restrictedPatientData: RestrictivePatient?) =
  PrisonerA().apply { this.translate(null, ob, Result.success(incentiveLevel), restrictedPatientData) }
fun PrisonerA(existingPrisoner: Prisoner?, ob: OffenderBooking, incentiveLevel: Result<IncentiveLevel?>, restrictedPatientData: RestrictivePatient?) =
  PrisonerA().apply { this.translate(existingPrisoner, ob, incentiveLevel, restrictedPatientData) }

fun PrisonerB(ob: OffenderBooking, incentiveLevel: IncentiveLevel?, restrictedPatientData: RestrictivePatient?) =
  PrisonerB().apply { this.translate(null, ob, Result.success(incentiveLevel), restrictedPatientData) }
fun PrisonerB(existingPrisoner: Prisoner?, ob: OffenderBooking, incentiveLevel: Result<IncentiveLevel?>, restrictedPatientData: RestrictivePatient?) =
  PrisonerB().apply { this.translate(existingPrisoner, ob, incentiveLevel, restrictedPatientData) }

fun Prisoner.translate(existingPrisoner: Prisoner?, ob: OffenderBooking, incentiveLevel: Result<IncentiveLevel?>, restrictedPatientData: RestrictivePatient?) {
  this.prisonerNumber = ob.offenderNo
  this.bookNumber = ob.bookingNo
  this.bookingId = ob.bookingId?.toString()
  this.pncNumber = ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value
  this.pncNumberCanonicalShort =
    ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value?.canonicalPNCNumberShort()
  this.pncNumberCanonicalLong =
    ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value?.canonicalPNCNumberLong()
  this.croNumber = ob.identifiers?.firstOrNull { i -> i.type == "CRO" }?.value

  this.cellLocation = ob.assignedLivingUnit?.description
  this.prisonName = ob.assignedLivingUnit?.agencyName
  this.prisonId = ob.agencyId
  this.status = ob.status
  this.inOutStatus = ob.inOutStatus
  this.lastMovementTypeCode = ob.lastMovementTypeCode
  this.lastMovementReasonCode = ob.lastMovementReasonCode

  this.category = ob.categoryCode
  this.csra = ob.csra

  this.dateOfBirth = ob.dateOfBirth
  this.firstName = ob.firstName
  this.middleNames = ob.middleName
  this.lastName = ob.lastName

  this.aliases =
    ob.aliases?.map { a -> PrisonerAlias(a.firstName, a.middleName, a.lastName, a.dob, a.gender, a.ethnicity) }
  this.alerts =
    ob.alerts?.filter { a -> a.active }?.map { a -> PrisonerAlert(a.alertType, a.alertCode, a.active, a.expired) }

  this.gender = ob.physicalAttributes?.gender
  this.ethnicity = ob.physicalAttributes?.ethnicity
  this.nationality = ob.profileInformation?.firstOrNull { p -> p.type == "NAT" }?.resultValue
  this.religion = ob.profileInformation?.firstOrNull { p -> p.type == "RELF" }?.resultValue
  this.maritalStatus = ob.profileInformation?.firstOrNull { p -> p.type == "MARITAL" }?.resultValue
  this.youthOffender =
    ob.profileInformation?.firstOrNull { p -> p.type == "YOUTH" }?.resultValue?.uppercase() == "YES"

  this.sentenceStartDate = ob.sentenceDetail?.sentenceStartDate
  this.confirmedReleaseDate = ob.sentenceDetail?.confirmedReleaseDate
  this.releaseDate = ob.sentenceDetail?.releaseDate
  this.sentenceExpiryDate = ob.sentenceDetail?.sentenceExpiryDate
  this.licenceExpiryDate = ob.sentenceDetail?.licenceExpiryDate
  this.homeDetentionCurfewEligibilityDate = ob.sentenceDetail?.homeDetentionCurfewEligibilityDate
  this.homeDetentionCurfewActualDate = ob.sentenceDetail?.homeDetentionCurfewActualDate
  this.homeDetentionCurfewEndDate = ob.sentenceDetail?.homeDetentionCurfewEndDate
  this.topupSupervisionStartDate = ob.sentenceDetail?.topupSupervisionStartDate
  this.topupSupervisionExpiryDate = ob.sentenceDetail?.topupSupervisionExpiryDate
  this.additionalDaysAwarded = ob.sentenceDetail?.additionalDaysAwarded
  this.nonDtoReleaseDate = ob.sentenceDetail?.nonDtoReleaseDate
  this.nonDtoReleaseDateType = ob.sentenceDetail?.nonDtoReleaseDateType
  this.tariffDate = ob.sentenceDetail?.tariffDate

  this.receptionDate = ob.receptionDate
  this.paroleEligibilityDate = ob.sentenceDetail?.paroleEligibilityDate
  this.automaticReleaseDate =
    ob.sentenceDetail?.automaticReleaseOverrideDate ?: ob.sentenceDetail?.automaticReleaseDate
  this.postRecallReleaseDate =
    ob.sentenceDetail?.postRecallReleaseOverrideDate ?: ob.sentenceDetail?.postRecallReleaseDate
  this.conditionalReleaseDate =
    ob.sentenceDetail?.conditionalReleaseOverrideDate ?: ob.sentenceDetail?.conditionalReleaseDate
  this.actualParoleDate = ob.sentenceDetail?.actualParoleDate

  this.locationDescription = restrictedPatientData
    ?.let { "${ob.locationDescription} - discharged to ${it.dischargedHospital?.description}" }
    ?: ob.locationDescription
  // get the most serious offence for this booking
  this.mostSeriousOffence =
    ob.offenceHistory?.firstOrNull { off -> off.mostSerious && off.bookingId == ob.bookingId }?.offenceDescription
  this.recall = ob.recall
  this.legalStatus = ob.legalStatus
  this.imprisonmentStatus = ob.imprisonmentStatus
  this.imprisonmentStatusDescription = ob.imprisonmentStatusDescription
  this.indeterminateSentence = ob.sentenceTerms?.any { st -> st.lifeSentence && st.bookingId == ob.bookingId }

  this.restrictedPatient = restrictedPatientData != null
  this.supportingPrisonId = restrictedPatientData?.supportingPrisonId
  this.dischargedHospitalId = restrictedPatientData?.dischargedHospital?.agencyId
  this.dischargedHospitalDescription = restrictedPatientData?.dischargedHospital?.description
  this.dischargeDate = restrictedPatientData?.dischargeDate
  this.dischargeDetails = restrictedPatientData?.dischargeDetails

  this.currentIncentive = incentiveLevel.map { it.toCurrentIncentive() }.getOrElse { existingPrisoner?.currentIncentive }
}

private fun IncentiveLevel?.toCurrentIncentive(): CurrentIncentive? = this?.let {
  CurrentIncentive(
    level = IncentiveLevel(it.iepCode, it.iepLevel),
    nextReviewDate = it.nextReviewDate,
    dateTime = it.iepTime.withNano(0) // ES only stores to the second
  )
}
