package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

@Schema(description = "Search Criteria for all prisoners at a prison")
data class PrisonId(

  @Schema(description = "Prison Id, Prison Id or OUT or TRN", example = "MDI")
  @NotBlank(message = "prisonId is mandatory")
  val prisonId: String?) {
}

