package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class PrisonerAlias (
  val firstName: String,
  val middleName: String?,
  val lastName: String,
  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  val dob: LocalDate,
  val gender: String?,
  val ethnicity: String?
)
