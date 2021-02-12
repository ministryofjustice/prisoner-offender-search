package uk.gov.justice.digital.hmpps.prisonersearch.services

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.BookingIds
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerListCriteria.PrisonerNumbers
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Size

@Schema(
  description = "Search Criteria for a list of prisoners",
  oneOf = [PrisonerNumbers::class, BookingIds::class],
  discriminatorProperty = "type",
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = PrisonerNumbers::class)
sealed class PrisonerListCriteria<out T>() {
  @Schema(hidden = true)
  abstract fun isValid(): Boolean
  @Schema(hidden = true)
  abstract fun values(): List<T>
  fun type() = this::class.simpleName!!

  @JsonTypeName("PrisonerNumbers")
  data class PrisonerNumbers(
    @Schema(description = "List of prisoner numbers to search by", example = "[\"A1234AA\"]")
    @NotEmpty
    @Size(min = 1, max = 1000)
    val prisonerNumbers: List<String>
  ) : PrisonerListCriteria<String>() {

    override fun isValid() = prisonerNumbers.isNotEmpty() && prisonerNumbers.size <= 1000

    override fun values() = prisonerNumbers

    @Schema(
      description = "PrisonerNumbers discriminator value",
      example = "PrisonerNumbers",
      allowableValues = ["PrisonerNumbers"]
    )
    val type: String = type()
  }

  @JsonTypeName("BookingIds")
  data class BookingIds(
    @Schema(description = "List of bookingIds to search by", example = "[1, 2, 3]")
    @NotEmpty
    @Size(min = 1, max = 1000)
    val values: List<Long>
  ) : PrisonerListCriteria<Long>() {

    override fun isValid() = values.isNotEmpty() && values.size <= 1000

    override fun values() = values

    @Schema(
      description = "BookingIds discriminator value",
      example = "BookingIds",
      allowableValues = ["BookingIds"]
    )
    val type: String = type()
  }
}
