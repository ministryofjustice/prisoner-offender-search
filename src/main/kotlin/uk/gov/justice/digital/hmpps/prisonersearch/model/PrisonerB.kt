package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import uk.gov.justice.digital.hmpps.prisonersearch.services.OffenderBooking
import java.time.LocalDate

@Document(indexName = "prisoner-search-b")
class PrisonerB(
  @Id
  @Field(type = FieldType.Keyword)
  override var prisonerId: String? = null,
  override var bookingId: Long? = null,
  @Field(type = FieldType.Keyword)
  override var bookingNo: String? = null,
  override var firstName: String? = null,
  override var lastName: String? = null,
  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  override var dateOfBirth: LocalDate? = null,
  @Field(type = FieldType.Keyword)
  override var agencyId: String? = null,
  @Field(type = FieldType.Boolean)
  override var active: Boolean = false
) : Prisoner {
  constructor(offenderBooking: OffenderBooking) : this(
    offenderBooking.offenderNo,
    offenderBooking.bookingId,
    offenderBooking.bookingNo,
    offenderBooking.firstName,
    offenderBooking.lastName,
    offenderBooking.dateOfBirth,
    offenderBooking.agencyId,
    offenderBooking.active
  )
}