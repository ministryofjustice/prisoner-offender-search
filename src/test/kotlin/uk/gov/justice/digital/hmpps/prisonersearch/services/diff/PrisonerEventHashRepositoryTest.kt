package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.toNullable
import java.time.Instant
import java.time.temporal.ChronoUnit

class PrisonerEventHashRepositoryTest : IntegrationTest() {

  private fun upsert(nomsNumber: String, hash: String, dateTime: Instant) = prisonerEventHashRepository.upsertPrisonerEventHashIfChanged(nomsNumber, hash, dateTime)
  private fun find(nomsNumber: String) = prisonerEventHashRepository.findById(nomsNumber).toNullable()
  private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS) // Postgres only handles microseconds, but some System clocks can go to nanoseconds.

  @Test
  @Transactional
  fun `should save a new prisoner event hash`() {
    val rowsUpdated = upsert("A1111AA", "aaa", now())
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aaa")
  }

  @Test
  @Transactional
  fun `should save multiple new prisoner event hashes`() {
    var rowsUpdated = upsert("A1111AA", "aaa", now())
    assertThat(rowsUpdated).isEqualTo(1)

    rowsUpdated = upsert("A2222AA", "bbb", now())
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A2222AA")
    assertThat(saved?.prisonerHash).isEqualTo("bbb")
  }

  @Test
  @Transactional
  fun `should update a changed prisoner event hash`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", "aaa", insertTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", "aab", updateTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aab")
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime)
  }

  @Test
  @Transactional
  fun `should not update an unchanged prisoner event hash`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", "aaa", insertTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", "aaa", updateTime)
    assertThat(rowsUpdated).isEqualTo(0)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aaa")
    assertThat(saved?.updatedDateTime).isEqualTo(insertTime)
  }

  @Test
  @Transactional
  fun `should update multiple existing prisoner event hashes`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", "aaa", insertTime)
    assertThat(rowsUpdated).isEqualTo(1)
    rowsUpdated = upsert("A2222AA", "bbb", insertTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", "aab", updateTime)
    assertThat(rowsUpdated).isEqualTo(1)
    rowsUpdated = upsert("A2222AA", "bbb", updateTime)
    assertThat(rowsUpdated).isEqualTo(0)
    val updateTime2 = updateTime.plusSeconds(1)
    rowsUpdated = upsert("A2222AA", "bbc", updateTime2)
    assertThat(rowsUpdated).isEqualTo(1)

    var saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aab")
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime)

    saved = find("A2222AA")
    assertThat(saved?.prisonerHash).isEqualTo("bbc")
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime2)
  }
}
