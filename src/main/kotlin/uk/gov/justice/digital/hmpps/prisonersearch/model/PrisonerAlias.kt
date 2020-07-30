package uk.gov.justice.digital.hmpps.prisonersearch.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class PrisonerAlias(
  @Schema(description = "First Name", example = "Robert")
  val firstName: String,

  @Schema(description = "Middle names", example = "Trevor")
  val middleNames: String?,

  @Schema(description = "Last name", example = "Lorsen")
  val lastName: String,

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  @Schema(description = "Date of birth", example = "1975-04-02")
  val dateOfBirth: LocalDate,

  @Schema(description = "Gender", example = "Male")
  val gender: String?,

  @Schema(description = "Ethnicity", example = "White : Irish")
  val ethnicity: String?
)
