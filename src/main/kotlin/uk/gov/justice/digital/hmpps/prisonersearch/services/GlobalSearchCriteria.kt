package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDate

@ApiModel("Search Criteria for Global Prisoner Search")
data class GlobalSearchCriteria (
  @ApiModelProperty(required = false, value = "Prisoner identifier, one of prisoner number, book number, booking ID or PNC", example = "A1234AA", position = 1)
  val prisonerIdentifier : String?,
  @ApiModelProperty(required = false, value = "First Name", example = "John", position = 2)
  val firstName : String?,
  @ApiModelProperty(required = false, value = "Last Name", example = "Smith", position = 3)
  val lastName: String?,
  @ApiModelProperty(required = false, value = "Gender", notes = "F - Female, M - Male, NK - Not Known / Not Recorded or NS - Not Specified (Indeterminate)", example = "M", position = 4)
  val gender: Gender?,
  @ApiModelProperty(required = false, value = "Location", notes = "All or Inside or Outside", example = "IN", position = 5)
  val location: String?,
  @ApiModelProperty(required = false, value = "Date of birth", example = "1970-02-28", position = 6)
  val dateOfBirth: LocalDate? = null,
  @ApiModelProperty(required = false, value = "Include aliases in search", example = "false", position = 7)
  val includeAliases : Boolean = false
) {

  fun isValid() = !(firstName.isNullOrBlank() && lastName.isNullOrBlank() && prisonerIdentifier.isNullOrBlank())
}

//offenderNo, lastName, firstName, gender, location, dateOfBirth, includeAliases


enum class Gender(val value: String?){

  M("Male"),
  F("Female"),
  NK("known"),
  NS("specified"),
  ALL("ALL");
}
