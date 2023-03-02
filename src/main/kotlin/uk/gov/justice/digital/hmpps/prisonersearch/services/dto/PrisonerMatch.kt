package uk.gov.justice.digital.hmpps.prisonersearch.services.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner

data class PrisonerMatch(
  @Schema(required = true, description = "Details of the matching prisoner")
  val prisoner: Prisoner,
)
