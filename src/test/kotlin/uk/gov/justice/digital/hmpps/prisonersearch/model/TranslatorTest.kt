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

  @Test
  fun `topupSupervisionStartDate is present`() {

    val tssDate = LocalDate.of(2021, 5, 15)
    val prisoner = translate(
      PrisonerA(),
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(topupSupervisionStartDate = tssDate)
      )
    )
    assertThat(prisoner.topupSupervisionStartDate).isEqualTo(tssDate)
  }

  @Test
  fun `homeDetentionCurfewEndDate is present`() {

    val hdcend = LocalDate.of(2021, 5, 15)
    val prisoner = translate(
      PrisonerA(),
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(homeDetentionCurfewEndDate = hdcend)
      )
    )
    assertThat(prisoner.homeDetentionCurfewEndDate).isEqualTo(hdcend)
  }

  @Test
  fun `when a prisoner has a sentence with dateOverride for conditionalRelease, automaticRelease and postRecallRelease then corresponding overrideDate is used`() {
    val conditionalReleaseOverrideDate = LocalDate.now().plusMonths(3)
    val automaticReleaseOverrideDate = LocalDate.now().plusMonths(2)
    val postRecallReleaseOverrideDate = LocalDate.now().plusMonths(1)
    val releaseDate = LocalDate.now().plusMonths(5)
    val prisoner = translate(
      PrisonerA(),
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(
          conditionalReleaseDate = releaseDate,
          conditionalReleaseOverrideDate = conditionalReleaseOverrideDate,
          automaticReleaseDate = releaseDate,
          automaticReleaseOverrideDate = automaticReleaseOverrideDate,
          postRecallReleaseDate = releaseDate,
          postRecallReleaseOverrideDate = postRecallReleaseOverrideDate
        ),
      )
    )
    assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseOverrideDate)
    assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseOverrideDate)
    assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseOverrideDate)
  }
  @Test
  fun `when a prisoner has a sentence with no dateOverride for conditionalRelease, automaticRelease and postRecallRelease then corresponding releaseDate is used`() {
    val conditionalReleaseDate = LocalDate.now().plusMonths(5)
    val automaticReleaseDate = LocalDate.now().plusMonths(4)
    val postRecallReleaseDate = LocalDate.now().plusMonths(3)
    val prisoner = translate(
      PrisonerA(),
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(
          conditionalReleaseDate = conditionalReleaseDate,
          automaticReleaseDate = automaticReleaseDate,
          postRecallReleaseDate = postRecallReleaseDate
        ),
      )
    )
    assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseDate)
    assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseDate)
    assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseDate)
  }

  @Test
  fun `imprisonmentStatus is present`() {
    val prisoner = translate(
      PrisonerA(),
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        imprisonmentStatus = "LIFE"
      )
    )
    assertThat(prisoner.imprisonmentStatus).isEqualTo("LIFE")
  }
}
