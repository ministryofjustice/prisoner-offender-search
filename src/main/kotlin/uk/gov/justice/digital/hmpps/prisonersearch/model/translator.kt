package uk.gov.justice.digital.hmpps.prisonersearch.model

import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking

fun translateA(offenderBooking: OffenderBooking): PrisonerA {

  return PrisonerA(
    offenderBooking.offenderNo,
    offenderBooking.bookingId,
    offenderBooking.bookingNo,
    offenderBooking.firstName,
    offenderBooking.middleName,
    offenderBooking.lastName,
    offenderBooking.dateOfBirth,
    offenderBooking.agencyId,
    offenderBooking.status
  )
}

fun translateB(offenderBooking: OffenderBooking): PrisonerB {
  return PrisonerB(
    offenderBooking.offenderNo,
    offenderBooking.bookingId,
    offenderBooking.bookingNo,
    offenderBooking.firstName,
    offenderBooking.middleName,
    offenderBooking.lastName,
    offenderBooking.dateOfBirth,
    offenderBooking.agencyId,
    offenderBooking.status
  )
}
