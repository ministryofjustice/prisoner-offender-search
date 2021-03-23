package uk.gov.justice.digital.hmpps.prisonersearch.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "index")
data class IndexProperties(val pageSize: Int, val completeThreshold: Long)
