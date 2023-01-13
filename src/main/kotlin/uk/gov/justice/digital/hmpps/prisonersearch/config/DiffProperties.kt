package uk.gov.justice.digital.hmpps.prisonersearch.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "diff")
data class DiffProperties(val telemetry: Boolean, val events: Boolean, val host: String)
