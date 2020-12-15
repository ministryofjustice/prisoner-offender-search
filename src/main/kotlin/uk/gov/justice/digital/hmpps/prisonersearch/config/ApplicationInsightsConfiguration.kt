package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Application insights now controlled by the spring-boot-starter dependency.  However when the key is not specified
 * we don't get a telemetry bean and application won't start.  Therefore need this backup configuration.
 */
@Configuration
open class ApplicationInsightsConfiguration {
  @Bean
  @Conditional(AppInsightKeyAbsentCondition::class)
  open fun telemetryClient(): TelemetryClient {
    return TelemetryClient()
  }

  class AppInsightKeyAbsentCondition : Condition {

    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
      val telemetryKey: String? = context.environment.getProperty("application.insights.ikey")
      return telemetryKey.isNullOrBlank()
    }
  }
}
