package uk.gov.justice.digital.hmpps.prisonersearch.model

import uk.gov.justice.digital.hmpps.prisonersearch.services.OffenderBooking

fun translateA(offenderBooking : OffenderBooking) =
  PrisonerA(
    offenderBooking.offenderNo,
    offenderBooking.bookingId,
    offenderBooking.bookingNo,
    offenderBooking.firstName,
    offenderBooking.lastName,
    offenderBooking.dateOfBirth,
    offenderBooking.agencyId,
    offenderBooking.active
  )

fun translateB(offenderBooking : OffenderBooking) =
  PrisonerB(
    offenderBooking.offenderNo,
    offenderBooking.bookingId,
    offenderBooking.bookingNo,
    offenderBooking.firstName,
    offenderBooking.lastName,
    offenderBooking.dateOfBirth,
    offenderBooking.agencyId,
    offenderBooking.active
  )