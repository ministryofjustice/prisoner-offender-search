package uk.gov.justice.digital.hmpps.prisonersearch.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

open class Prisoner {
  @Id
  @Field(type = FieldType.Keyword)
  @Schema(required = true, description = "Prisoner Number", example = "A1234AA")
  var prisonerNumber: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "PNC Number", example = "12/394773H")
  var pncNumber: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "PNC Number", example = "12/394773H")
  var pncNumberCanonicalShort: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "PNC Number", example = "2012/394773H")
  var pncNumberCanonicalLong: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "CRO Number", example = "29906/12J")
  var croNumber: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "Booking No.", example = "0001200924")
  var bookingId: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "Book Number", example = "38412A")
  var bookNumber: String? = null

  @Schema(required = true, description = "First Name", example = "Robert")
  var firstName: String? = null

  @Schema(description = "Middle Names", example = "John James")
  var middleNames: String? = null

  @Schema(required = true, description = "Last name", example = "Larsen")
  var lastName: String? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(required = true, description = "Date of Birth", example = "1975-04-02")
  var dateOfBirth: LocalDate? = null

  @Schema(required = true, description = "Gender", example = "Female")
  var gender: String? = null

  @Schema(required = true, description = "Ethnicity", example = "White: Eng./Welsh/Scot./N.Irish/British")
  var ethnicity: String? = null

  @Schema(required = true, description = "Youth Offender?", example = "true")
  var youthOffender: Boolean? = null

  @Schema(required = true, description = "Marital Status", example = "Widowed")
  var maritalStatus: String? = null

  @Schema(required = true, description = "Religion", example = "Church of England (Anglican)")
  var religion: String? = null

  @Schema(required = true, description = "Nationality", example = "Egyptian")
  var nationality: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(required = true, description = "Status of the prisoner", example = "ACTIVE IN")
  var status: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "Prison ID", example = "MDI")
  var prisonId: String? = null

  @Schema(description = "Prison Name", example = "HMP Leeds")
  var prisonName: String? = null

  @Schema(description = "In prison cell location", example = "A-1-002")
  var cellLocation: String? = null

  @Field(type = FieldType.Nested)
  @Schema(description = "Aliases Names and Details")
  var aliases: List<PrisonerAlias>? = null

  @Field(type = FieldType.Nested)
  @Schema(description = "Alerts")
  var alerts: List<PrisonerAlert>? = null

  @Schema(description = "Cell Sharing Risk Assessment", example = "HIGH")
  var csra: String? = null

  @Schema(description = "Prisoner Category", example = "C")
  var category: String? = null

  @Schema(
    description = "Legal Status",
    example = "SENTENCED",
    allowableValues = ["RECALL", "DEAD", "INDETERMINATE_SENTENCE", "SENTENCED", "CONVICTED_UNSENTENCED", "CIVIL_PRISONER", "IMMIGRATION_DETAINEE", "REMAND", "UNKNOWN", "OTHER"]
  )
  var legalStatus: String? = null

  @Schema(required = true, description = "Most serious offence for this sentence", example = "Robbery")
  var mostSeriousOffence: String? = null

  @Schema(description = "Indicates that the offender has been recalled", example = "false")
  var recall: Boolean? = null

  @Schema(description = "Indicates the the offender has an indeterminate sentence", example = "true")
  var indeterminateSentence: Boolean? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Start Date for this sentence", example = "2020-04-03")
  var sentenceStartDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Actual of most likely Release Date", example = "2023-05-02")
  var releaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Release Date Confirmed", example = "2023-05-01")
  var confirmedReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Sentence Expiry Date", example = "2023-05-01")
  var sentenceExpiryDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Licence Expiry Date", example = "2023-05-01")
  var licenceExpiryDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "HDC Eligibility Date", example = "2023-05-01")
  var homeDetentionCurfewEligibilityDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "HDC Actual Date", example = "2023-05-01")
  var homeDetentionCurfewActualDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "HDC End Date", example = "2023-05-02")
  var homeDetentionCurfewEndDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Top-up supervision start date", example = "2023-04-29")
  var topupSupervisionStartDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Top-up supervision expiry date", example = "2023-05-01")
  var topupSupervisionExpiryDate: LocalDate? = null

  @Schema(description = "Days added to sentence term due to adjustments.", example = "10")
  var additionalDaysAwarded: Int? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(
    description = "Release date for Non determinant sentence (if applicable). This will be based on one of ARD, CRD, NPD or PRRD.",
    example = "2023-05-01"
  )
  var nonDtoReleaseDate: LocalDate? = null

  @Schema(
    description = "Indicates which type of non-DTO release date is the effective release date. One of 'ARD’, 'CRD’, ‘NPD’ or 'PRRD’.",
    example = "ARD",
    allowableValues = ["ARD", "CRD", "NPD", "PRRD"]
  )
  var nonDtoReleaseDateType: String? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Date prisoner was received into the prison", example = "2023-05-01")
  var receptionDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Parole  Eligibility Date", example = "2023-05-01")
  var paroleEligibilityDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Automatic Release Date. If automaticReleaseOverrideDate is available then it will be set as automaticReleaseDate", example = "2023-05-01")
  var automaticReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Post Recall Release Date. if postRecallReleaseOverrideDate is available then it will be set as postRecallReleaseDate", example = "2023-05-01")
  var postRecallReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Conditional Release Date. If conditionalReleaseOverrideDate is available then it will be set as conditionalReleaseDate", example = "2023-05-01")
  var conditionalReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.date)
  @Schema(description = "Actual Parole Date", example = "2023-05-01")
  var actualParoleDate: LocalDate? = null

  @Schema(
    description = "current prison or outside with last movement information.",
    example = "Outside - released from Leeds"
  )
  var locationDescription: String? = null
}

@Document(indexName = "prisoner-search-a")
class PrisonerA : Prisoner()

@Document(indexName = "prisoner-search-b")
class PrisonerB : Prisoner()
