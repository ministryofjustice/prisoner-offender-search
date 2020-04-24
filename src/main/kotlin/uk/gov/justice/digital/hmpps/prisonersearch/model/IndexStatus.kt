package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

@Document(indexName = "offender-index-status")
class IndexStatus (
  @Id
  @Field(type = FieldType.Keyword)
  var id: String = "STATUS",

  @Field(type = FieldType.Keyword)
  var currentIndex : String?,

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd HH:mm:ss")
  var startIndexTime: LocalDateTime?,

  @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd HH:mm:ss")
  var endIndexTime: LocalDateTime?,

  @Field(type = FieldType.Boolean)
  var inProgress : Boolean

)