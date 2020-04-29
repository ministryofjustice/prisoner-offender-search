package uk.gov.justice.digital.hmpps.prisonersearch.services.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class NomisApiHealth(webClient: WebClient,
                     @Value("\${api.base.url.nomis}") baseUri: String,
                     @Value("\${api.health-timeout:2s}") timeout: Duration)
    : HealthCheck(webClient, baseUri, timeout)