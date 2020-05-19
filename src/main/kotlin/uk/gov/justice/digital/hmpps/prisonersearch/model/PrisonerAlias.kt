package uk.gov.justice.digital.hmpps.prisonersearch.model

import io.swagger.annotations.ApiModelProperty
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class PrisonerAlias (
  @ApiModelProperty(required = true, value = "First Name", example = "Robert", position = 1)
  val firstName: String,

  @ApiModelProperty(value = "Middle names", example = "Trevor", position = 2)
  val middleNames: String?,

  @ApiModelProperty(required = true, value = "Last name", example = "Lorsen", position = 3)
  val lastName: String,

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  @ApiModelProperty(required = true, value = "Date of birth", example = "1975-04-02", position = 4)
  val dateOfBirth: LocalDate,

  @ApiModelProperty(value = "Gender", example = "Male", position = 5)
  val gender: String?,

  @ApiModelProperty(value = "Ethnicity", example = "White : Irish", position = 6)
  val ethnicity: String?
)
