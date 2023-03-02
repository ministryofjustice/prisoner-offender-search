package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Size

sealed class PrisonerListCriteria<out T>() {
  @Schema(hidden = true)
  abstract fun isValid(): Boolean

  @Schema(hidden = true)
  abstract fun values(): List<T>

  @JsonIgnore
  @Schema(hidden = true)
  val type = this::class.simpleName!!

  data class PrisonerNumbers(
    @Schema(description = "List of prisoner numbers to search by", example = "[\"A1234AA\"]")
    @NotEmpty
    @Size(min = 1, max = 1000)
    val prisonerNumbers: List<String>,
  ) : PrisonerListCriteria<String>() {

    override fun isValid() = prisonerNumbers.isNotEmpty() && prisonerNumbers.size <= 1000

    override fun values() = prisonerNumbers
  }

  data class BookingIds(
    @Schema(description = "List of bookingIds to search by", example = "[1, 2, 3]")
    @NotEmpty
    @Size(min = 1, max = 1000)
    val bookingIds: List<Long>,
  ) : PrisonerListCriteria<Long>() {

    override fun isValid() = bookingIds.isNotEmpty() && bookingIds.size <= 1000

    override fun values() = bookingIds
  }
}
