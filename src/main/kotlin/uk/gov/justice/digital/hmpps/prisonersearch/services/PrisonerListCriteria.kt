package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("Search Criteria for a list of prisoners")

@JsonIgnoreProperties( value =  [ "isValid" ])
data class PrisonerListCriteria (
  @ApiModelProperty(required = false, value = "List of prisoner numbers to search by", example = "[\"A1234AA\"]", position = 1)
  val prisonerNumbers : List<String>

) {
  val isValid: Boolean
    get() = prisonerNumbers.isNotEmpty() && prisonerNumbers.size <= 200

}
