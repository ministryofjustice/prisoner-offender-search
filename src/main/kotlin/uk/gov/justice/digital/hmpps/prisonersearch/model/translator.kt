package uk.gov.justice.digital.hmpps.prisonersearch.model

import uk.gov.justice.digital.hmpps.prisonersearch.services.canonicalPNCNumberLong
import uk.gov.justice.digital.hmpps.prisonersearch.services.canonicalPNCNumberShort
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking

fun <P:Prisoner> translate(prisoner : P, ob: OffenderBooking): P {
  prisoner.prisonerNumber = ob.offenderNo
  prisoner.bookNumber = ob.bookingNo
  prisoner.bookingId = ob.bookingId?.toString()
  prisoner.pncNumber = ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value
  prisoner.pncNumber = ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value
  prisoner.pncNumberCanonicalShort = ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value?.canonicalPNCNumberShort()
  prisoner.pncNumberCanonicalLong = ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value?.canonicalPNCNumberLong()
  prisoner.croNumber = ob.identifiers?.firstOrNull { i -> i.type == "CRO" }?.value

  prisoner.cellLocation = ob.assignedLivingUnit?.description
  prisoner.prisonName = ob.assignedLivingUnit?.agencyName
  prisoner.prisonId = ob.agencyId
  prisoner.status = ob.status

  prisoner.category = ob.categoryCode
  prisoner.csra = ob.csra

  prisoner.dateOfBirth = ob.dateOfBirth
  prisoner.firstName = ob.firstName
  prisoner.middleNames = ob.middleName
  prisoner.lastName = ob.lastName

  prisoner.aliases = ob.aliases?.map { a -> PrisonerAlias(a.firstName, a.middleName, a.lastName, a.dob, a.gender, a.ethnicity) }
  prisoner.alerts = ob.alerts?.filter { a -> a.active }?.map { a -> PrisonerAlert(a.alertType, a.alertCode) }

  prisoner.gender = ob.physicalAttributes?.gender
  prisoner.ethnicity = ob.physicalAttributes?.ethnicity
  prisoner.nationality = ob.profileInformation?.firstOrNull { p -> p.type == "NAT" }?.resultValue
  prisoner.religion = ob.profileInformation?.firstOrNull { p -> p.type == "RELF" }?.resultValue
  prisoner.maritalStatus = ob.profileInformation?.firstOrNull { p -> p.type == "MARITAL" }?.resultValue
  prisoner.youthOffender = ob.profileInformation?.firstOrNull { p -> p.type == "YOUTH" }?.resultValue?.toUpperCase() == "YES"

  prisoner.sentenceStartDate = ob.sentenceDetail?.sentenceStartDate
  prisoner.confirmedReleaseDate = ob.sentenceDetail?.confirmedReleaseDate
  prisoner.releaseDate = ob.sentenceDetail?.releaseDate
  prisoner.sentenceExpiryDate = ob.sentenceDetail?.sentenceExpiryDate
  prisoner.licenceExpiryDate = ob.sentenceDetail?.licenceExpiryDate
  prisoner.homeDetentionCurfewEligibilityDate = ob.sentenceDetail?.homeDetentionCurfewEligibilityDate
  prisoner.homeDetentionCurfewActualDate = ob.sentenceDetail?.homeDetentionCurfewActualDate
  prisoner.additionalDaysAwarded = ob.sentenceDetail?.additionalDaysAwarded
  prisoner.nonDtoReleaseDate = ob.sentenceDetail?.nonDtoReleaseDate
  prisoner.nonDtoReleaseDateType = ob.sentenceDetail?.nonDtoReleaseDateType

  prisoner.receptionDate = ob.receptionDate
  prisoner.paroleEligibilityDate = ob.sentenceDetail?.paroleEligibilityDate
  prisoner.automaticReleaseDate = ob.sentenceDetail?.automaticReleaseDate
  prisoner.postRecallReleaseDate = ob.sentenceDetail?.postRecallReleaseDate
  prisoner.conditionalReleaseDate = ob.sentenceDetail?.conditionalReleaseDate
  prisoner.actualParoleDate = ob.sentenceDetail?.actualParoleDate

  prisoner.locationDescription = ob.locationDescription

  // get the most serious offence for this booking
  prisoner.mostSeriousOffence = ob.offenceHistory?.firstOrNull{ off -> off.mostSerious && off.bookingId == ob.bookingId }?.offenceDescription
  prisoner.recall = ob.recall
  prisoner.legalStatus = ob.legalStatus
  prisoner.indeterminateSentence = ob.sentenceTerms?.any{ st -> st.lifeSentence && st.bookingId == ob.bookingId }
  return prisoner
}

