package uk.gov.justice.digital.hmpps.prisonersearch.resource

import io.swagger.v3.parser.OpenAPIV3Parser
import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.web.server.LocalServerPort
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

  fun `the security scheme is setup for bearer tokens`() {
    val bearerJwts = JSONArray()
    bearerJwts.addAll(listOf("read", "write"))
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.bearer-jwt.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.bearer-jwt.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.bearer-jwt.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.security[0].bearer-jwt")
      .isEqualTo(bearerJwts)
  }
}
