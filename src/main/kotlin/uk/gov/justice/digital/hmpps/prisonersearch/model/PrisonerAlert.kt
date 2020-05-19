package uk.gov.justice.digital.hmpps.prisonersearch.model

import io.swagger.annotations.ApiModelProperty

data class PrisonerAlert (
  @ApiModelProperty(required = true, value = "Alert Type", example = "H", position = 1)
  val alertType: String,
  @ApiModelProperty(required = true, value = "Alert Code", example = "HA", position = 2)
  val alertCode: String
)