package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PrisonerDifferencesRepositoryTest {
  @Autowired
  lateinit var repository: PrisonerDifferencesRepository

  @BeforeEach
  fun clearPreviousDifferenceData() {
    repository.deleteAll()
  }

  @Test
  @Transactional
  fun `should save a new prisoner differences record`() {
    val prisonerDifferences = repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[]"))
    assertThat(prisonerDifferences.nomsNumber).isEqualTo("A1111AA")

    val saved = repository.findByIdOrNull(prisonerDifferences.prisonerDifferencesId)
    assertThat(saved?.differences).isEqualTo("[]")
  }

  @Test
  @Transactional
  fun `should find differences by noms number`() {
    repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[first]"))
    repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[second]"))

    val saved = repository.findByNomsNumber("A1111AA")
    assertThat(saved)
      .hasSize(2)
      .extracting(PrisonerDifferences::differences)
      .containsExactlyInAnyOrder(Tuple("[first]"), Tuple("[second]"))
  }

  @Test
  @Transactional
  fun `should find differences by date time`() {
    val now = Instant.now()
    repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[first]", dateTime = now.minusSeconds(60)))
    repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[second]", dateTime = now))

    val saved = repository.findByDateTimeBetween(now.minusSeconds(1), now)
    assertThat(saved)
      .hasSize(1)
      .extracting(PrisonerDifferences::differences)
      .containsExactlyInAnyOrder(Tuple("[second]"))
  }

  @Test
  @Transactional
  fun `should delete all records before given date`() {
    val now = Instant.now()
    repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[first]", dateTime = now.minusSeconds(60)))
    repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[second]", dateTime = now))

    val deleted = repository.deleteByDateTimeBefore(now.minusSeconds(1))
    assertThat(deleted).isEqualTo(1)
    assertThat(repository.findByNomsNumber("A1111AA")).hasSize(1)
  }
}
