package uk.gov.justice.digital.hmpps.prisonersearch.model

enum class SyncIndex(val indexName : String) {

  INDEX_A("prisoner-search-a"), INDEX_B("prisoner-search-b");

  fun otherIndex() : SyncIndex {
    return if (this == INDEX_A) INDEX_B else INDEX_A
  }
}
