package uk.gov.justice.digital.hmpps.prisonersearch.model

import io.swagger.annotations.ApiModelProperty
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

open class Prisoner {

  @Id
  @Field(type = FieldType.Keyword)
  @ApiModelProperty(required = true, value = "Prisoner Number", example = "A1234AA", position = 1)
  var prisonerNumber: String? = null

  @Field(type = FieldType.Keyword)
  @ApiModelProperty(value = "PNC Number", example = "12/394773H", position = 2)
  var pncNumber: String? = null

  @Field(type = FieldType.Keyword)
  @ApiModelProperty(value = "CRO Number", example = "29906/12J", position = 3)
  var croNumber: String? = null

  @Field(type = FieldType.Keyword)
  @ApiModelProperty(value = "Booking No.", example = "0001200924", position = 4)
  var bookingId: String? = null

  @Field(type = FieldType.Keyword)
  @ApiModelProperty(value = "Book Number", example = "38412A", position = 5)
  var bookNumber: String? = null

  @ApiModelProperty(required = true, value = "First Name", example = "Robert", position = 6)
  var firstName: String? = null

  @ApiModelProperty(value = "Middle Names", example = "John James", position = 7)
  var middleNames: String? = null

  @ApiModelProperty(required = true, value = "Last name", example = "Larsen", position = 8)
  var lastName: String? = null

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  @ApiModelProperty(required = true, value = "Date of Birth", example = "1975-04-02", position = 9)
  var dateOfBirth: LocalDate? = null

  @ApiModelProperty(required = true, value = "Gender", example = "Female", position = 10)
  var gender: String? = null
  @ApiModelProperty(required = true, value = "Ethnicity", example = "White: Eng./Welsh/Scot./N.Irish/British", position = 11)
  var ethnicity: String? = null
  @ApiModelProperty(required = true, value = "Youth Offender?", example = "true", position = 12)
  var youthOffender: Boolean? = null
  @ApiModelProperty(required = true, value = "Marital Status", example = "Widowed", position = 13)
  var maritalStatus: String? = null
  @ApiModelProperty(required = true, value = "Religion", example = "Church of England (Anglican)", position = 14)
  var religion: String? = null
  @ApiModelProperty(required = true, value = "Nationality", example = "Egyptian", position = 15)
  var nationality: String? = null

  @Field(type = FieldType.Keyword)
  @ApiModelProperty(required = true, value = "Status of the prisoner", example = "ACTIVE IN", position = 16)
  var status: String? = null

  @Field(type = FieldType.Keyword)
  @ApiModelProperty(value = "Prison ID", example = "MDI", position = 17)
  var prisonId: String? = null

  @ApiModelProperty(value = "Prison Name", example = "HMP Leeds", position = 18)
  var prisonName: String? = null

  @ApiModelProperty(value = "In prison cell location", example = "A-1-002", position = 19)
  var cellLocation: String? = null

  @Field(type = FieldType.Nested)
  @ApiModelProperty(value = "Aliases Names and Details", position = 19)
  var aliases: List<PrisonerAlias>? = null

  @Field(type = FieldType.Nested)
  @ApiModelProperty(value = "Alerts", position = 20)
  var alerts: List<PrisonerAlert>? = null

  @ApiModelProperty(value = "Cell Sharing Risk Assessment", example = "HIGH", position = 22)
  var csra: String? = null

  @ApiModelProperty(value = "Prisoner Category", example = "C", position = 23)
  var category: String? = null

  @ApiModelProperty(value = "Legal Status of Remand or Conviction", example = "REMAND", allowableValues = "REMAND,CONVICTED", position = 24)
  var legalStatus: String? = null

  @ApiModelProperty(required = true, value = "Most serious offence for this sentence", example = "Robbery", position = 25)
  var mostSeriousOffence : String? = null

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  @ApiModelProperty(value = "Start Date for this sentence", example = "2020-04-03", position = 26)
  var sentenceStartDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  @ApiModelProperty(value = "Actual of most likely Release Date", example = "2023-05-02", position = 27)
  var releaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  @ApiModelProperty(value = "Release Date Confirmed", example = "2023-05-01", position = 28)
  var confirmedReleaseDate: LocalDate? = null
}

@Document(indexName = "prisoner-search-a")
class PrisonerA : Prisoner()

@Document(indexName = "prisoner-search-b")
class PrisonerB : Prisoner()