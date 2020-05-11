package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("Search Criteria for Prisoner Search")
data class SearchCriteria (
  @ApiModelProperty(required = false, value = "Prisoner identifier, one of prisoner number, book number, booking ID or PNC", example = "A1234AA,", position = 1)
  val prisonerIdentifier : String?,
  @ApiModelProperty(required = false, value = "First Name", example = "John", position = 2)
  val firstName : String?,
  @ApiModelProperty(required = false, value = "Last Name", example = "Smith", position = 3)
  val lastName: String?,
  @ApiModelProperty(required = false, value = "Prison Id", notes = "Prison Id or OUT or TRN", example = "MDI", position = 4)
  val prisonId: String? = null,
  @ApiModelProperty(required = false, value = "Include aliases in search", example = "false", position = 5)
  val includeAliases : Boolean = false
) {
  val isValid: Boolean
    get() = !(firstName.isNullOrBlank() && lastName.isNullOrBlank() && prisonerIdentifier.isNullOrBlank())

}
