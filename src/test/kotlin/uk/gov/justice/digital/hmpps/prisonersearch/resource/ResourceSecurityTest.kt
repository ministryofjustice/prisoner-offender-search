package uk.gov.justice.digital.hmpps.prisonersearch.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import uk.gov.justice.digital.hmpps.prisonersearch.integration.IntegrationTest
import java.io.File
import kotlin.reflect.full.memberFunctions

class ResourceSecurityTest : IntegrationTest() {
  @Autowired
  private lateinit var context: ApplicationContext

  private val unprotectedDefaultMethods = setOf(
    "GET /v3/api-docs.yaml",
    "GET /swagger-ui.html",
    "GET /v3/api-docs",
    "GET /v3/api-docs/swagger-config",
    " /error",
  )
  private val testClasses = setOf(
    PrisonerSearchByBookingIdsResourceTest::class,
    PrisonerSearchByReleaseDateResourceTest::class,
    PossibleMatchesSearchResourceTest::class,
    PrisonerSearchBySinglePrisonResourceTest::class,
    PrisonerMatchResourceTest::class,
    RestrictedPatientsSearchResourceTest::class,
    PrisonerSearchByPrisonerNumbersResourceTest::class,
    PrisonerSearchResourceTest::class,
  )

  @Test
  fun `Ensure all endpoints protected with PreAuthorize`() {
    // need to exclude any that are forbidden in helm configuration
    val exclusions = File("helm_deploy").walk().filter { it.name.equals("values.yaml") }.flatMap { file ->
      file.readLines().map { line ->
        line.takeIf { it.contains("location") }?.substringAfter("location ")?.substringBefore(" {")
      }
    }.filterNotNull().flatMap { path -> listOf("GET", "POST", "PUT", "DELETE").map { "$it $path" } }
      .toMutableSet().also {
        it.addAll(unprotectedDefaultMethods)
      }

    testClasses.flatMap { clazz ->
      clazz.nestedClasses.toMutableList().apply { add(clazz) }.flatMap { testClazz ->
        testClazz.memberFunctions
          .filter { it.name.contains("access forbidden") }
          .map {
            it.name
              .substringAfter("for endpoint ")
              .substringBefore(" when no role")
              .replace("#", "/")
          }
      }
    }.also { exclusions.addAll(it) }

    context.getBeansOfType(RequestMappingHandlerMapping::class.java).forEach { (_, mapping) ->
      mapping.handlerMethods.forEach { (mappingInfo, method) ->
        val classAnnotation = method.beanType.getAnnotation(PreAuthorize::class.java)
        val annotation = method.getMethodAnnotation(PreAuthorize::class.java)
        println("Found $mappingInfo with class annotation $classAnnotation and annotation $annotation")
        if (classAnnotation == null && annotation == null) {
          mappingInfo.getMappings().forEach {
            assertThat(exclusions.contains(it)).withFailMessage {
              "Found $mappingInfo of type $method with no PreAuthorize annotation"
            }.isTrue()
          }
        }
      }
    }
  }
}

private fun RequestMappingInfo.getMappings() =
  methodsCondition.methods.map { it.name }.flatMap {
      method ->
    pathPatternsCondition.patternValues.map { "$method $it" }
  }
