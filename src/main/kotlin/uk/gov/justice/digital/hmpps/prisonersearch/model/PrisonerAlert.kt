package uk.gov.justice.digital.hmpps.prisonersearch.model

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerAlert(
  @Schema(description = "Alert Type", example = "H")
  val alertType: String,
  @Schema(description = "Alert Code", example = "HA")
  val alertCode: String,
  @Schema(description = "Active", example = "true")
  val active: Boolean,
  @Schema(description = "Expired", example = "true")
  val expired: Boolean
)
