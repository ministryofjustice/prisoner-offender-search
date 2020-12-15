package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.SentenceDetail
import java.time.LocalDate

class TranslatorTest {

  @Test
  fun `when prisoner has no booking associated the booking information is missing`() {

    val dateOfBirth = LocalDate.now().minusYears(18)
    val prisoner = translate(PrisonerA(), OffenderBooking("A1234AA", "Fred", "Bloggs", dateOfBirth, false))

    assertThat(prisoner.prisonerNumber).isEqualTo("A1234AA")
    assertThat(prisoner.firstName).isEqualTo("Fred")
    assertThat(prisoner.lastName).isEqualTo("Bloggs")
    assertThat(prisoner.dateOfBirth).isEqualTo(dateOfBirth)
    assertThat(prisoner.bookingId).isNull()
  }

  @Test
  fun `topupSupervisionExpiryDate is present`() {

    val tseDate = LocalDate.of(2021, 5, 15)
    val prisoner = translate(
      PrisonerA(),
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(topupSupervisionExpiryDate = tseDate)
      )
    )
    assertThat(prisoner.topupSupervisionExpiryDate).isEqualTo(tseDate)
  }
}
