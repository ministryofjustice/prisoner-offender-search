package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex

class PrisonerIndexResourceTest : QueueIntegrationTest() {

  @BeforeEach
  fun init() {
    resetStubs()
    setupIndexes()
  }

  @Test
  fun `access forbidden when no authority`() {

    webTestClient.put().uri("/prisoner-index/build-index")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {

    webTestClient.put().uri("/prisoner-index/build-index")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `can index a prison with correct role`() {

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-status.startIndexTime").doesNotHaveJsonPath()
      .jsonPath("index-status.endIndexTime").doesNotHaveJsonPath()

    indexPrisoners()

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-status.startIndexTime").isNotEmpty
      .jsonPath("index-status.endIndexTime").doesNotHaveJsonPath()
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-status.startIndexTime").isNotEmpty
      .jsonPath("index-status.endIndexTime").isNotEmpty
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")
  }

  @Test
  fun `can cancel and re-index`() {

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")

    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/cancel-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")
  }


  @Test
  fun `can index a new prisoner`() {
    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")

    webTestClient.put().uri("/prisoner-index/index/prisoner/A5432AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json("/results/new_prisoner.json".readResourceAsText())

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("19")

  }

  @Test
  fun `both indexes are maintained whilst indexing but not once completed`() {

    //index B
    indexPrisoners()

    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    resetStubs()
    // Start indexing A
    indexPrisoners()

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("18")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("18")

    webTestClient.put().uri("/prisoner-index/index/prisoner/A5432AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_B.name)
      .jsonPath("index-status.inProgress").isEqualTo("true")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("19")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("19")


    webTestClient.put().uri("/prisoner-index/mark-complete")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.put().uri("/prisoner-index/index/prisoner/A5432AB")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.currentIndex").isEqualTo(SyncIndex.INDEX_A.name)
      .jsonPath("index-status.inProgress").isEqualTo("false")
      .jsonPath("index-size.${SyncIndex.INDEX_A.name}").isEqualTo("20")
      .jsonPath("index-size.${SyncIndex.INDEX_B.name}").isEqualTo("19")

  }

}

private fun String.readResourceAsText(): String = PrisonerIndexResourceTest::class.java.getResource(this).readText()
