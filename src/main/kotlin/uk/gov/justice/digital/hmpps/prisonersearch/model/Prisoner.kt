package uk.gov.justice.digital.hmpps.prisonersearch.model

import java.time.LocalDate

interface Prisoner {
  var prisonerNumber: String?
  val bookingId: Long?
  val bookNumber: String?
  val firstName: String?
  val middleNames: String?
  val lastName: String?
  val dateOfBirth: LocalDate?
  val prisonId: String?
  val status: String
}