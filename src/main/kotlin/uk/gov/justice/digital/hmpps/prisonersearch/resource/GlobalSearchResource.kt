package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiImplicitParams
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.services.GlobalSearchService

@RestController
@Validated
class GlobalSearchResource(
  val globalSearchService: GlobalSearchService
) {

  @PostMapping("/global-search", produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
  @ApiOperation(value = "Match prisoners by criteria", notes = "Requires GLOBAL_SEARCH role")
  @ApiImplicitParams(
    ApiImplicitParam(name = "page", dataType = "int", paramType = "query", value = "Results page you want to retrieve (0..N)", example = "0", defaultValue = "0"),
    ApiImplicitParam(name = "size", dataType = "int", paramType = "query", value = "Number of records per page.", example = "10", defaultValue = "10")
  )
  fun globalFindByCriteria(
      @ApiParam(required = true, name = "globalSearchCriteria") @RequestBody globalSearchCriteria: GlobalSearchCriteria,
      @PageableDefault()pageable: Pageable
  ): Page<Prisoner> {
    return globalSearchService.findByGlobalSearchCriteria(globalSearchCriteria, pageable)
  }
}
