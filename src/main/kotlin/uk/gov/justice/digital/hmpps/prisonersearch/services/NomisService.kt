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
class NomisService(val prisonWebClient: WebClient,
                      @Value("\${api.offender.timeout:5s}") val offenderTimeout: Duration) {

    private val ids = object : ParameterizedTypeReference<List<OffenderId>>() {}
  
    fun getOffendersIds(offset: Int = 0, size: Int = 10): List<OffenderId>? {
        return prisonWebClient.get()
            .uri("/api/offenders/ids")
            .header("Page-Offset", offset.toString())
            .header("Page-Limit", size.toString())
            .retrieve()
            .bodyToMono(ids)
            .block(offenderTimeout.multipliedBy(24))
    }

  fun getOffender(bookingId: Long): OffenderBooking? {
    return prisonWebClient.get()
        .uri("/api/bookings/$bookingId?extraInfo=true")
        .retrieve()
        .bodyToMono(OffenderBooking::class.java)
        .block(offenderTimeout)
  }

  fun getOffender(offenderNo: String): OffenderBooking? {
    return prisonWebClient.get()
        .uri("/api/offenders/$offenderNo")
        .retrieve()
        .bodyToMono(OffenderBooking::class.java)
        .onErrorResume(NotFound::class.java) { Mono.empty() }
        .block(offenderTimeout)
  }
}

data class OffenderId (
    val offenderNumber: String
)

