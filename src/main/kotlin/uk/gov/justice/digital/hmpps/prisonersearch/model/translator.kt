package uk.gov.justice.digital.hmpps.prisonersearch.model

import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking

fun <P:Prisoner> translate(prisoner : P, ob: OffenderBooking): P {
  prisoner.prisonerNumber = ob.offenderNo
  prisoner.bookNumber = ob.bookingNo
  prisoner.bookingId = ob.bookingId.toString()
  prisoner.pncNumber = ob.identifiers?.firstOrNull { i -> i.type == "PNC" }?.value

  prisoner.cellLocation = ob.assignedLivingUnit?.description
  prisoner.prisonName = ob.assignedLivingUnit?.agencyName
  prisoner.prisonId = ob.agencyId

  prisoner.category = ob.assessments?.firstOrNull{ a -> a.assessmentCode == "CATEGORY"}?.classificationCode
  prisoner.csra = ob.assessments?.firstOrNull{ a -> a.cellSharingAlertFlag }?.classificationCode

  prisoner.dateOfBirth = ob.dateOfBirth
  prisoner.firstName = ob.firstName
  prisoner.middleNames = ob.middleName
  prisoner.lastName = ob.lastName

  prisoner.aliases = ob.aliases?.map { a -> PrisonerAlias(a.firstName, a.middleName, a.lastName, a.dob, a.gender, a.ethnicity) }

  prisoner.gender = ob.physicalAttributes?.gender
  prisoner.ethnicity = ob.physicalAttributes?.ethnicity
  prisoner.nationality = ob.profileInformation?.firstOrNull { p -> p.type == "NAT" }?.resultValue
  prisoner.religion = ob.profileInformation?.firstOrNull { p -> p.type == "RELF" }?.resultValue
  prisoner.maritalStatus = ob.profileInformation?.firstOrNull { p -> p.type == "MARITAL" }?.resultValue
  prisoner.youthOffender = ob.profileInformation?.firstOrNull { p -> p.type == "YOUTH" }?.resultValue == "Y"
  prisoner.disability = ob.profileInformation?.firstOrNull { p -> p.type == "DISABILITY" }?.resultValue == "Y"

  prisoner.shoeSize = ob.physicalCharacteristics?.firstOrNull{ pc -> pc.type == "SHOESIZE"}?.detail?.toIntOrNull()

  prisoner.legalStatus = ob.legalStatus
  prisoner.confirmedReleaseDate = ob.sentenceDetail?.confirmedReleaseDate
  prisoner.releaseDate = ob.sentenceDetail?.releaseDate

  // get the most serious offence for this booking
  prisoner.mostSeriousOffence = ob.offenceHistory?.firstOrNull{ off -> off.mostSerious && off.bookingId == ob.bookingId }?.offenceDescription
  return prisoner
}

