package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.services.toNullable
import java.time.Instant
import java.time.temporal.ChronoUnit

class PrisonerEventHashRepositoryTest : IntegrationTest() {

  @Autowired
  private lateinit var prisonerEventHashRepository: PrisonerEventHashRepository

  private fun upsert(nomsNumber: String, hash: Int, dateTime: Instant) = prisonerEventHashRepository.upsertPrisonerEventHashIfChanged(nomsNumber, hash, dateTime)
  private fun find(nomsNumber: String) = prisonerEventHashRepository.findById(nomsNumber).toNullable()
  private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS) // Postgres only handles microseconds, but some System clocks can go to nanoseconds.

  @Test
  @Transactional
  fun `should save a new prisoner event hash`() {
    val rowsUpdated = upsert("A1111AA", 111, now())
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo(111)
  }

  @Test
  @Transactional
  fun `should save multiple new prisoner event hashes`() {
    var rowsUpdated = upsert("A1111AA", 111, now())
    assertThat(rowsUpdated).isEqualTo(1)

    rowsUpdated = upsert("A2222AA", 222, now())
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A2222AA")
    assertThat(saved?.prisonerHash).isEqualTo(222)
  }

  @Test
  @Transactional
  fun `should update a changed prisoner event hash`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", 111, insertTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", 112, updateTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo(112)
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime)
  }

  @Test
  @Transactional
  fun `should not update an unchanged prisoner event hash`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", 111, insertTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", 111, updateTime)
    assertThat(rowsUpdated).isEqualTo(0)

    val saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo(111)
    assertThat(saved?.updatedDateTime).isEqualTo(insertTime)
  }

  @Test
  @Transactional
  fun `should update multiple existing prisoner event hashes`() {
    val insertTime = now().minusSeconds(1)
    var rowsUpdated = upsert("A1111AA", 111, insertTime)
    assertThat(rowsUpdated).isEqualTo(1)
    rowsUpdated = upsert("A2222AA", 222, insertTime)
    assertThat(rowsUpdated).isEqualTo(1)

    val updateTime = insertTime.plusSeconds(1)
    rowsUpdated = upsert("A1111AA", 112, updateTime)
    assertThat(rowsUpdated).isEqualTo(1)
    rowsUpdated = upsert("A2222AA", 222, updateTime)
    assertThat(rowsUpdated).isEqualTo(0)
    rowsUpdated = upsert("A2222AA", 223, updateTime)
    assertThat(rowsUpdated).isEqualTo(1)

    var saved = find("A1111AA")
    assertThat(saved?.prisonerHash).isEqualTo(112)
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime)

    saved = find("A2222AA")
    assertThat(saved?.prisonerHash).isEqualTo(223)
    assertThat(saved?.updatedDateTime).isEqualTo(updateTime)
  }
}
