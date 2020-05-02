package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.elasticsearch.annotations.Document
import java.time.LocalDate

@Document(indexName = "prisoner-search-b")
class PrisonerB(
  prisonerNumber: String?,
  bookingId: Long?,
  bookNumber: String?,
  firstName: String?,
  middleNames: String?,
  lastName: String?,
  dateOfBirth: LocalDate?,
  prisonId: String?,
  status: String?
) : Prisoner(prisonerNumber, bookingId, bookNumber, firstName, middleNames, lastName, dateOfBirth, prisonId, status)