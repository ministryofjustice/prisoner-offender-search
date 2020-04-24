package uk.gov.justice.digital.hmpps.prisonersearch.model


import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.FieldType.Keyword
import java.time.LocalDate

@Document(indexName = "prisoner-search-b")
class PrisonerB (
    @Id
    @Field(type = Keyword)
    override var prisonerId: String? = null,
    override val bookingId: Long? = null,
    @Field(type = Keyword)
    override val bookingNo: String? = null,
    override val firstName: String? = null,
    override val lastName: String? = null,
    @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
    override val dateOfBirth: LocalDate? = null,
    @Field(type = Keyword)
    override val agencyId: String? = null,
    @Field(type = FieldType.Boolean)
    override val active: Boolean
) : Prisoner

