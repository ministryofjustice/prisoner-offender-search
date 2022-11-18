package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Service
class IncentivesService(
  val incentivesWebClient: WebClient,
  @Value("\${api.incentives.timeout:20s}") val timeout: Duration
)
