package uk.gov.justice.digital.hmpps.prisonersearch.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate
import java.time.LocalDateTime

data class CurrentIncentive(
  @Schema(description = "Incentive level")
  val level: IncentiveLevel,
  @Schema(description = "Days since last review", example = "120")
  val daysSinceReview: Long,
  @Field(type = FieldType.Date, format = [DateFormat.date_time])
  @Schema(required = true, description = "Date time of the incentive", example = "2022-11-10T15:47:24.682335")
  val dateTime: LocalDateTime,
  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(required = true, description = "Schedule new review date", example = "2022-11-10")
  val nextReviewDate: LocalDate? = null
)

data class IncentiveLevel(
  @Schema(description = "description", example = "Standard")
  val description: String,
  // TODO add code when available from API
)
