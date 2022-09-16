package uk.gov.justice.digital.hmpps.prisonersearch.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "diff")
data class DiffProperties(val telemetry: Boolean, val host: String)
