package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import java.time.LocalDate

class TranslatorTest {

  @Test
  fun `when prisoner has no booking associated the booking information is missing`() {

    val dateOfBirth = LocalDate.now().minusYears(18)
    val prisoner = translate(PrisonerA(), OffenderBooking("A1234AA", "Fred", "Bloggs", dateOfBirth, false))

    Assertions.assertAll(
        Executable { Assertions.assertEquals("A1234AA", prisoner.prisonerNumber) },
        Executable { Assertions.assertEquals("Fred", prisoner.firstName) },
        Executable { Assertions.assertEquals("Bloggs", prisoner.lastName) },
        Executable { Assertions.assertEquals(dateOfBirth, prisoner.dateOfBirth) },
        Executable { Assertions.assertNull(prisoner.bookingId) }
    )

  }
}