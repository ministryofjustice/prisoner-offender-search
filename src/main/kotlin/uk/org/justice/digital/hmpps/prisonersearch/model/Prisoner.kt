package uk.org.justice.digital.hmpps.prisonersearch.model


import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "prisoner-search", type = "prisoner")
class Prisoner (
    @Id
    var prisonerId: String? = null,
    val bookingId: Long? = null,
    val bookingNo: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    //    @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
    val dateOfBirth: String? = null,
    val agencyId: String? = null,
    val active: Boolean = false
)