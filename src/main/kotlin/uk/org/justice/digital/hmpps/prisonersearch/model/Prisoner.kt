package uk.org.justice.digital.hmpps.prisonersearch.model


import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

@Document(indexName = "prisoner-search", type = "prisoner")
class Prisoner (
    @Id
    var prisonerId: String? = null,
    val bookingId: Long? = null,
    val bookingNo: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
    val dateOfBirth: LocalDate? = null,
    val agencyId: String? = null,
    val active: Boolean = false
)