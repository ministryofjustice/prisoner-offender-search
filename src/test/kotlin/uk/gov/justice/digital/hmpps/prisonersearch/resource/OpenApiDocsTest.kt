package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.parser.OpenAPIV3Parser
import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpenApiDocsTest : IntegrationTest() {
  @LocalServerPort
  private var port: Int = 0

  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the swagger json is valid`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("messages").doesNotExist()
  }

  @Test
  fun `the open api json is valid and contains documentation`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)
    assertThat(result.messages).isEmpty()
    assertThat(result.openAPI.paths).isNotEmpty
  }

  @Test
  fun `the swagger json contains the version number`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("info.version").isEqualTo(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
  }

  @Test
  fun `the generated swagger for date times hasn't got the time zone`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.schemas.IndexStatus.properties.startIndexTime.example").isEqualTo("2021-07-05T10:35:17")
      .jsonPath("$.components.schemas.IndexStatus.properties.startIndexTime.type").isEqualTo("string")
      .jsonPath("$.components.schemas.IndexStatus.properties.startIndexTime.pattern")
      .isEqualTo("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}${'$'}""")
      .jsonPath("$.components.schemas.IndexStatus.properties.startIndexTime.format").doesNotExist()
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_VIEW_PRISONER_DATA role`() {
    val viewPrisonerDataRole = JSONArray()
    viewPrisonerDataRole.addAll(listOf("read"))
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.description").value(Matchers.containsString("ROLE_VIEW_PRISONER_DATA"))
      .jsonPath("$.security[0].view-prisoner-data-role")
      .isEqualTo(viewPrisonerDataRole)
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_PRISONER_SEARCH role`() {
    val prisonerSearchRole = JSONArray()
    prisonerSearchRole.addAll(listOf("read"))
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.description").value(Matchers.containsString("ROLE_PRISONER_SEARCH"))
      .jsonPath("$.security[1].prisoner-search-role")
      .isEqualTo(prisonerSearchRole)
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_GLOBAL_SEARCH role`() {
    val globalSearchRole = JSONArray()
    globalSearchRole.addAll(listOf("read"))
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.global-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.global-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.global-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.global-search-role.description").value(Matchers.containsString("ROLE_GLOBAL_SEARCH"))
      .jsonPath("$.security[2].global-search-role")
      .isEqualTo(globalSearchRole)
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_PRISONER_IN_PRISON_SEARCH role`() {
    val prisonerInPrisonSearchRole = JSONArray()
    prisonerInPrisonSearchRole.addAll(listOf("read"))
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.description").value(Matchers.containsString("ROLE_PRISONER_IN_PRISON_SEARCH"))
      .jsonPath("$.security[3].prisoner-in-prison-search-role")
      .isEqualTo(prisonerInPrisonSearchRole)
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the PRISONER_INDEX role`() {
    val prisonerIndexRole = JSONArray()
    prisonerIndexRole.addAll(listOf("read", "write"))
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-index-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-index-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-index-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-index-role.description").value(Matchers.containsString("PRISONER_INDEX"))
      .jsonPath("$.security[4].prisoner-index-role")
      .isEqualTo(prisonerIndexRole)
  }
}
