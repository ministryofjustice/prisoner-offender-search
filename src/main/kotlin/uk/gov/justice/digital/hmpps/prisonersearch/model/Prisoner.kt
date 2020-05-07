package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

abstract class Prisoner {

  @Id
  @Field(type = FieldType.Keyword)
  var prisonerNumber: String? = null

  @Field(type = FieldType.Keyword)
  var pncNumber: String? = null

  var bookingId: String? = null

  @Field(type = FieldType.Keyword)
  var bookNumber: String? = null

  var firstName: String? = null
  var middleNames: String? = null
  var lastName: String? = null
  @Field(type = FieldType.Nested)
  var aliases: List<PrisonerAlias>? = null

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  var dateOfBirth: LocalDate? = null

  @Field(type = FieldType.Keyword)
  var prisonId: String? = null
  var status: String? = null
  var cellLocation: String? = null
  var prisonName: String? = null

  var gender: String? = null
  var ethnicity: String? = null
  var shoeSize: Int? = null
  var youthOffender: Boolean? = null
  var maritalStatus: String? = null
  var disability: Boolean? = null
  var religion: String? = null
  var nationality: String? = null

  var csra: String? = null
  var category: String? = null

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  var confirmedReleaseDate: LocalDate? = null
  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd")
  var releaseDate: LocalDate? = null
  var legalStatus: String? = null

  var mostSeriousOffence : String? = null
}

@Document(indexName = "prisoner-search-a")
class PrisonerA : Prisoner()

@Document(indexName = "prisoner-search-b")
class PrisonerB : Prisoner()