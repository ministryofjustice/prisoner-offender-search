package uk.gov.justice.digital.hmpps.prisonersearch.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.Duration

@Service
class NomisService(
  val prisonWebClient: WebClient,
  @Value("\${api.offender.timeout:20s}") val offenderTimeout: Duration
) {

  private val ids = object : ParameterizedTypeReference<List<OffenderId>>() {}
  private val identifiers = object : ParameterizedTypeReference<List<BookingIdentifier>>() {}

  fun getOffendersIds(offset: Long = 0, size: Int = 10): OffenderResponse {
    return prisonWebClient.get()
      .uri("/api/offenders/ids")
      .header("Page-Offset", offset.toString())
      .header("Page-Limit", size.toString())
      .exchange()
      .block(offenderTimeout.multipliedBy(6))?.let {
        OffenderResponse(
          it.bodyToMono(ids).block(),
          it.headers().header("Total-Records").first().toLongOrNull() ?: 0
        )
      } ?: OffenderResponse()
  }

  fun getNomsNumberForBooking(bookingId: Long): String? {
    return prisonWebClient.get()
      .uri("/api/bookings/v2?bookingId=$bookingId")
      .retrieve()
      .bodyToMono(BookingInfo::class.java)
      .onErrorResume(NotFound::class.java) { Mono.empty() }
      .block(offenderTimeout)
      .content.firstOrNull()?.offenderNo
  }

  fun getOffender(offenderNo: String): OffenderBooking? {
    return prisonWebClient.get()
      .uri("/api/offenders/$offenderNo")
      .retrieve()
      .bodyToMono(OffenderBooking::class.java)
      .onErrorResume(NotFound::class.java) { Mono.empty() }
      .block(offenderTimeout)
  }

  fun getMergedIdentifiersByBookingId(bookingId: Long): List<BookingIdentifier>? {
    return prisonWebClient.get()
      .uri("/api/bookings/$bookingId/identifiers?type=MERGED")
      .retrieve()
      .bodyToMono(identifiers)
      .onErrorResume(NotFound::class.java) { Mono.empty() }
      .block(offenderTimeout)
  }
}
data class BookingInfo(
  val content: List<BookingContent> = listOf()
)

data class BookingContent(
  val offenderNo: String
)

data class OffenderId(
  val offenderNumber: String
)

data class OffenderResponse(
  val offenderIds: List<OffenderId>? = emptyList(),
  val totalRows: Long = 0
)

data class BookingIdentifier(
  val type: String,
  val value: String
)
