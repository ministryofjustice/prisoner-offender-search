package uk.org.justice.digital.hmpps.prisonersearch.model


import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.FieldType.Keyword
import uk.org.justice.digital.hmpps.prisonersearch.services.OffenderBooking
import java.time.LocalDate

@Document(indexName = "prisoner-search", type = "prisoner")
class Prisoner (
    @Id
    @Field(type = Keyword)
    var prisonerId: String? = null,
    val bookingId: Long? = null,
    @Field(type = Keyword)
    val bookingNo: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
    val dateOfBirth: LocalDate? = null,
    @Field(type = Keyword)
    val agencyId: String? = null,
    val active: Boolean
)

fun translate(offenderBooking : OffenderBooking) =
    Prisoner(
        offenderBooking.offenderNo,
        offenderBooking.bookingId,
        offenderBooking.bookingNo,
        offenderBooking.firstName,
        offenderBooking.lastName,
        offenderBooking.dateOfBirth,
        offenderBooking.agencyId,
        offenderBooking.active
    )