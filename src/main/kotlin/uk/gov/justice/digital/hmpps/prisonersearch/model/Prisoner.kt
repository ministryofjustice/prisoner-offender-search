package uk.gov.justice.digital.hmpps.prisonersearch.model

import java.time.LocalDate

interface Prisoner {
  var prisonerId: String?
  val bookingId: Long?
  val bookingNo: String?
  val firstName: String?
  val lastName: String?
  val dateOfBirth: LocalDate?
  val agencyId: String?
  val active: Boolean
}