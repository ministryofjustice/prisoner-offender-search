package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.toNullable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class PrisonerEventHashRepositoryTest : IntegrationTest() {

  private fun upsert(nomsNumber: String, hash: String, dateTime: Instant, updatedIdentifier: String) = prisonerEventHashRepository.upsertPrisonerEventHashIfChanged(nomsNumber, hash, dateTime, updatedIdentifier)
  private fun find(nomsNumber: String) = prisonerEventHashRepository.findById(nomsNumber).toNullable()
  private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS) // Postgres only handles microseconds, but some System clocks can go to nanoseconds.

  @Test
  @Transactional
  fun `should save a new prisoner event hash`() {
    val updatedIdentifier = UUID.randomUUID().toString()
    val rowsUpdated = upsert("A1111AA", "aaa", now(), updatedIdentifier)
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aaa")
    assertThat(saved?.updatedIdentifier).isEqualTo(updatedIdentifier)
  }

  @Test
  @Transactional
  fun `should save multiple new prisoner event hashes`() {
    var rowsUpdated = upsert("A1111AA", "aaa", now(), UUID.randomUUID().toString())
    assertThat(rowsUpdated).isEqualTo(1)

    rowsUpdated = upsert("A2222AA", "bbb", now(), UUID.randomUUID().toString())
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A2222AA")
    assertThat(saved?.prisonerHash).isEqualTo("bbb")
  }

  @Test
  @Transactional
  fun `should update a changed prisoner event hash`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", "aaa", insertTime, UUID.randomUUID().toString())
    assertThat(rowsUpdated).isEqualTo(1)

    val updatedIdentifier = UUID.randomUUID().toString()
    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", "aab", updateTime, updatedIdentifier)
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aab")
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime)
    assertThat(saved?.updatedIdentifier).isEqualTo(updatedIdentifier)
  }

  @Test
  @Transactional
  fun `should not update an unchanged prisoner event hash`() {
    val insertTime = now().minusSeconds(1)
    val updatedIdentifier = UUID.randomUUID().toString()
    var rowsUpdated = upsert("A1111AA", "aaa", insertTime, updatedIdentifier)
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", "aaa", updateTime, UUID.randomUUID().toString())
    assertThat(rowsUpdated).isEqualTo(0)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aaa")
    assertThat(saved?.updatedDateTime).isEqualTo(insertTime)
    assertThat(saved?.updatedIdentifier).isEqualTo(updatedIdentifier)
  }

  @Test
  @Transactional
  fun `should update multiple existing prisoner event hashes`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", "aaa", insertTime, UUID.randomUUID().toString())
    assertThat(rowsUpdated).isEqualTo(1)
    rowsUpdated = upsert("A2222AA", "bbb", insertTime, UUID.randomUUID().toString())
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    val updatedIdentifier = UUID.randomUUID().toString()
    rowsUpdated = upsert("A1111AA", "aab", updateTime, updatedIdentifier)
    assertThat(rowsUpdated).isEqualTo(1)
    rowsUpdated = upsert("A2222AA", "bbb", updateTime, UUID.randomUUID().toString())
    assertThat(rowsUpdated).isEqualTo(0)
    val updateTime2 = updateTime.plusSeconds(1)
    val updatedIdentifier2 = UUID.randomUUID().toString()
    rowsUpdated = upsert("A2222AA", "bbc", updateTime2, updatedIdentifier2)
    assertThat(rowsUpdated).isEqualTo(1)

    var saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo("aab")
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime)
    assertThat(saved?.updatedIdentifier).isEqualTo(updatedIdentifier)

    saved = find("A2222AA")
    assertThat(saved?.prisonerHash).isEqualTo("bbc")
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime2)
    assertThat(saved?.updatedIdentifier).isEqualTo(updatedIdentifier2)
  }
}
