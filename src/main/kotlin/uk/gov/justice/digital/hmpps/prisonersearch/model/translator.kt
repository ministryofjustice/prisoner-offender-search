package uk.gov.justice.digital.hmpps.prisonersearch.model

import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking

fun translate(offenderBooking: OffenderBooking, index: SyncIndex): Prisoner {
  if (index == SyncIndex.INDEX_A) {
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
