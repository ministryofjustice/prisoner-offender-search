package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class IncentivesService(
  val incentivesWebClient: WebClient,
  @Value("\${api.incentives.timeout:20s}") val timeout: Duration,
  @Value("\${index.incentives:true}") val indexIncentives: Boolean
) {
  fun getCurrentIncentive(bookingId: Long): IncentiveLevel? = if (indexIncentives) {
    incentivesWebClient.get().uri("/iep/reviews/booking/{bookingId}?with-details=false", bookingId)
      .retrieve()
      .bodyToMono(IncentiveLevel::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block(timeout)
  } else null
}

data class IncentiveLevel(
  val iepCode: String,
  val iepLevel: String,
  val iepTime: LocalDateTime,
  val nextReviewDate: LocalDate?,
)
