package uk.gov.justice.digital.hmpps.prisonersearch.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomiser
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://prisoner-offender-search-dev.prison.service.justice.gov.uk").description("Development"),
        Server().url("https://prisoner-offender-search-preprod.prison.service.justice.gov.uk").description("PreProd"),
        Server().url("https://prisoner-offender-search.prison.service.justice.gov.uk").description("Prod"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .tags(
      listOf(
        Tag().name("Popular")
          .description("The most popular endpoints. Look here first when deciding which endpoint to use."),
        Tag().name("Establishment search").description("Endpoints for searching for a prisoner within a prison"),
        Tag().name("Global search")
          .description("Endpoints for searching for a prisoner across the entire prison estate, including people that have previously been released"),
        Tag().name("Batch").description("Endpoints designed to find a large number of prisoners with a single call"),
        Tag().name("Matching").description("Endpoints designed for matching a prisoner with data from other sources"),
        Tag().name("Deprecated")
          .description("Endpoints that should no longer be used and will be removed in a future release"),
        Tag().name("Specific use case")
          .description("Endpoints that were designed for a specific use case and are unlikely to fit for general use"),
        Tag().name("Experimental")
          .description("Endpoints that have not been tried and tested in a production environment"),
        Tag().name("Elastic Search index maintenance").description("Endpoints that are to be used by administrators only for maintaining Elasticsearch indices"),
        Tag().name("hmpps-queue-resource").description("""Endpoints that are to be used by administrators only for managing SQS queues. All endpoints require the <b>QUEUE_ADMIN</b> role further information can be found in the <a href="https://github.com/ministryofjustice/hmpps-spring-boot-sqs">hmpps-spring-boot-sqs</a> project"""),
        Tag().name("hmpps-queue-resource-async").description("""Endpoints that are to be used by administrators only for managing SQS queues. All endpoints require the <b>QUEUE_ADMIN</b> role further information can be found in the <a href="https://github.com/ministryofjustice/hmpps-spring-boot-sqs">hmpps-spring-boot-sqs</a> project"""),
      ),
    )
    .info(
      Info().title("Prisoner Search").version(version)
        .description(this.javaClass.getResource("/documentation/service-description.html").readText())
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .components(
      Components().addSecuritySchemes(
        "view-prisoner-data-role",
        SecurityScheme()
          .type(SecurityScheme.Type.HTTP)
          .scheme("bearer")
          .bearerFormat("JWT")
          .`in`(SecurityScheme.In.HEADER)
          .name("Authorization")
          .description("A HMPPS Auth access token with the `ROLE_VIEW_PRISONER_DATA` role."),
      ).addSecuritySchemes(
        "prisoner-search-role",
        SecurityScheme()
          .type(SecurityScheme.Type.HTTP)
          .scheme("bearer")
          .bearerFormat("JWT")
          .`in`(SecurityScheme.In.HEADER)
          .name("Authorization")
          .description("A HMPPS Auth access token with the `ROLE_PRISONER_SEARCH` role."),
      ).addSecuritySchemes(
        "global-search-role",
        SecurityScheme()
          .type(SecurityScheme.Type.HTTP)
          .scheme("bearer")
          .bearerFormat("JWT")
          .`in`(SecurityScheme.In.HEADER)
          .name("Authorization")
          .description("A HMPPS Auth access token with the `ROLE_GLOBAL_SEARCH` role."),
      ).addSecuritySchemes(
        "prisoner-in-prison-search-role",
        SecurityScheme()
          .type(SecurityScheme.Type.HTTP)
          .scheme("bearer")
          .bearerFormat("JWT")
          .`in`(SecurityScheme.In.HEADER)
          .name("Authorization")
          .description("A HMPPS Auth access token with the `ROLE_PRISONER_IN_PRISON_SEARCH` role."),
      ).addSecuritySchemes(
        "prisoner-index-role",
        SecurityScheme()
          .type(SecurityScheme.Type.HTTP)
          .scheme("bearer")
          .bearerFormat("JWT")
          .`in`(SecurityScheme.In.HEADER)
          .name("Authorization")
          .description("A HMPPS Auth access token with the `PRISONER_INDEX` role."),
      ),
    )
    .addSecurityItem(SecurityRequirement().addList("view-prisoner-data-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("prisoner-search-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("global-search-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("prisoner-in-prison-search-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("prisoner-index-role", listOf("read", "write")))

  @Bean
  fun openAPICustomiser(): OpenApiCustomiser = OpenApiCustomiser {
    it.components.schemas.forEach { (_, schema: Schema<*>) ->
      val properties = schema.properties ?: mutableMapOf()
      for (propertyName in properties.keys) {
        val propertySchema = properties[propertyName]!!
        if (propertySchema is DateTimeSchema) {
          properties.replace(
            propertyName,
            StringSchema()
              .example("2021-07-05T10:35:17")
              .pattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")
              .description(propertySchema.description)
              .required(propertySchema.required),
          )
        }
      }
    }
  }
}
