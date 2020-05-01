package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

@Document(indexName = "prisoner-search-b")
class PrisonerB(
  @Id
  @Field(type = FieldType.Keyword)
  override var prisonerNumber: String? = null,
  override var bookingId: Long? = null,
  @Field(type = FieldType.Keyword)
  override var bookNumber: String? = null,
  override var firstName: String? = null,
  override var middleNames: String? = null,
  override var lastName: String? = null,
  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  override var dateOfBirth: LocalDate? = null,
  @Field(type = FieldType.Keyword)
  override var prisonId: String? = null,
  @Field(type = FieldType.Boolean)
  override var status: String

) : Prisoner