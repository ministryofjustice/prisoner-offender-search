package uk.org.justice.digital.hmpps.prisonersearch.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.LocalDate

@Service
class NomisService(val prisonWebClient: WebClient,
                      @Value("\${api.offender.timeout:10s}") val offenderTimeout: Duration) {

  fun getOffender(bookingId: Long): OffenderBooking? {
    return prisonWebClient.get()
        .uri("/api/bookings/$bookingId")
        .retrieve()
        .bodyToMono(OffenderBooking::class.java)
        .block(offenderTimeout)
  }

  fun getOffender(nomsId: String): OffenderBooking? {
    return prisonWebClient.get()
        .uri("/api/bookings/offenderNo/$nomsId")
        .retrieve()
        .bodyToMono(OffenderBooking::class.java)
        .block(offenderTimeout)
  }


}

data class OffenderBooking (
    val offenderNo: String,
    val bookingId: Long,
    val bookingNo: String?,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate,
    val agencyId: String?,
    val active: Boolean
)