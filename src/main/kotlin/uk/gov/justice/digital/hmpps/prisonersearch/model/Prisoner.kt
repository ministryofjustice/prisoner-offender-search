package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

abstract class Prisoner(
  @Id
  @Field(type = FieldType.Keyword)
  var prisonerNumber: String? = null,
  var bookingId: Long? = null,
  @Field(type = FieldType.Keyword)
  var bookNumber: String? = null,
  var firstName: String? = null,
  var middleNames: String? = null,
  var lastName: String? = null,
  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  var dateOfBirth: LocalDate? = null,
  @Field(type = FieldType.Keyword)
  var prisonId: String? = null,
  var status: String? = null
)