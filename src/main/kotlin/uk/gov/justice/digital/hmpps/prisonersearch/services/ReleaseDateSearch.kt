package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.services.exceptions.BadRequestException
import java.time.LocalDate
import javax.validation.constraints.NotNull

@Schema(description = "Search Criteria for Release Date Search")
data class ReleaseDateSearch(
  @Schema(description = "The lower bound for the release date range of which to search - defaults to today if not provided", example = "20/04/2022")
  @JsonFormat(pattern = "dd/MM/yyyy")
  val earliestReleaseDate: LocalDate? = LocalDate.now(),
  @Schema(description = "The upper bound for the release date range of which to search. A required field.", example = "20/05/2022")
  @JsonFormat(pattern = "dd/MM/yyyy")
  @field:NotNull(message = "latestReleaseDateRange is a required field")
  val latestReleaseDate: LocalDate?,
  @Schema(description = "List of Prison Ids (can include OUT and TRN) to restrict the search by. Unrestricted if not supplied or null", example = "[\"MDI\"]")
  val prisonIds: Set<String>? = null,
) {
  @Schema(hidden = true)
  fun validate() = run {
    if (latestReleaseDate?.isBefore(earliestReleaseDate) == true) {
      throw BadRequestException("Invalid search - latestReleaseDate must be on or before the earliestReleaseDate")
    }
  }
}
