package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

@Document(indexName = "offender-index-status")
class IndexStatus(
  @Id
  @Field(type = FieldType.Keyword)
  var id: String = "STATUS",

  @Field(type = FieldType.Keyword)
  var currentIndex: SyncIndex,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  var startIndexTime: LocalDateTime?,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  var endIndexTime: LocalDateTime?,

  @Field(type = FieldType.Boolean)
  var inProgress: Boolean

) {

  fun toggleIndex() {
    currentIndex = if (currentIndex == SyncIndex.INDEX_A) SyncIndex.INDEX_B else SyncIndex.INDEX_A
  }
}