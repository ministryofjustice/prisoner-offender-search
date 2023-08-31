package uk.gov.justice.digital.hmpps.prisonersearch.resource

import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.repository.PrisonerDifferencesRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class PrisonerDifferencesResourceTest : IntegrationTest() {
  @Autowired
  private lateinit var repository: PrisonerDifferencesRepository

  @BeforeEach
  fun clearPrisonerDifferences() {
    repository.deleteAll()
  }

  @Nested
  @DisplayName("GET /prisoner-differences")
  inner class GetPrisonerDifferences {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/prisoner-differences")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/prisoner-differences")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can find all the differences`() {
      repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[first]"))
      repository.save(PrisonerDifferences(nomsNumber = "A1111AB", differences = "[second]"))

      webTestClient.get().uri("/prisoner-differences")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody().jsonPath("$.[*].nomsNumber").value<JSONArray> {
          assertThat(it.toList()).containsExactlyInAnyOrder("A1111AA", "A1111AB")
        }.jsonPath("$.[*].differences").value<JSONArray> {
          assertThat(it.toList()).containsExactlyInAnyOrder("[first]", "[second]")
        }
    }
  }

  @Nested
  @DisplayName("DELETE /prisoner-differences/delete")
  inner class DeletePrisonerDifferences {
    @Test
    fun `endpoint deletes old data`() {
      val overAMonth = Instant.now().minus(32, ChronoUnit.DAYS)
      repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[first]", dateTime = overAMonth))
      repository.save(PrisonerDifferences(nomsNumber = "A1111AB", differences = "[second]", dateTime = overAMonth))
      repository.save(PrisonerDifferences(nomsNumber = "A1111AA", differences = "[recent]"))

      // note no roles required by the endpoint - protected by ingress config instead
      webTestClient.delete().uri("/prisoner-differences/delete")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody().json("2")

      assertThat(repository.findByNomsNumber("A1111AA"))
        .hasSize(1)
        .extracting(PrisonerDifferences::differences)
        .containsExactly(tuple("[recent]"))
    }
  }
}
