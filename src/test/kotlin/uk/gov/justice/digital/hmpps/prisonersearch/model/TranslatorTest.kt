package uk.gov.justice.digital.hmpps.prisonersearch.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.services.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.RestrictivePatient
import uk.gov.justice.digital.hmpps.prisonersearch.services.dto.SentenceDetail
import java.time.LocalDate
import java.time.LocalDateTime

class TranslatorTest {

  @Test
  fun `when prisoner has no booking associated the booking information is missing`() {

    val dateOfBirth = LocalDate.now().minusYears(18)
    val prisoner = PrisonerA(
      ob = OffenderBooking("A1234AA", "Fred", "Bloggs", dateOfBirth, false),
      incentiveLevel = null,
      restrictedPatientData = null
    )

    assertThat(prisoner.prisonerNumber).isEqualTo("A1234AA")
    assertThat(prisoner.firstName).isEqualTo("Fred")
    assertThat(prisoner.lastName).isEqualTo("Bloggs")
    assertThat(prisoner.dateOfBirth).isEqualTo(dateOfBirth)
    assertThat(prisoner.bookingId).isNull()
  }

  @Test
  fun `topupSupervisionExpiryDate is present`() {

    val tseDate = LocalDate.of(2021, 5, 15)
    val prisoner = PrisonerA(
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(topupSupervisionExpiryDate = tseDate)
      ),
      incentiveLevel = null,
      restrictedPatientData = null
    )
    assertThat(prisoner.topupSupervisionExpiryDate).isEqualTo(tseDate)
  }

  @Test
  fun `topupSupervisionStartDate is present`() {

    val tssDate = LocalDate.of(2021, 5, 15)
    val prisoner = PrisonerA(
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(topupSupervisionStartDate = tssDate)
      ),
      incentiveLevel = null,
      restrictedPatientData = null
    )
    assertThat(prisoner.topupSupervisionStartDate).isEqualTo(tssDate)
  }

  @Test
  fun `homeDetentionCurfewEndDate is present`() {

    val hdcend = LocalDate.of(2021, 5, 15)
    val prisoner = PrisonerA(
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(homeDetentionCurfewEndDate = hdcend)
      ),
      incentiveLevel = null,
      restrictedPatientData = null
    )
    assertThat(prisoner.homeDetentionCurfewEndDate).isEqualTo(hdcend)
  }

  @Test
  fun `when a prisoner has a sentence with dateOverride for conditionalRelease, automaticRelease and postRecallRelease then corresponding overrideDate is used`() {
    val conditionalReleaseOverrideDate = LocalDate.now().plusMonths(3)
    val automaticReleaseOverrideDate = LocalDate.now().plusMonths(2)
    val postRecallReleaseOverrideDate = LocalDate.now().plusMonths(1)
    val releaseDate = LocalDate.now().plusMonths(5)
    val prisoner = PrisonerA(
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
      ),
      incentiveLevel = null,
      restrictedPatientData = null
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
    val prisoner = PrisonerA(
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
      ),
      incentiveLevel = null,
      restrictedPatientData = null
    )
    assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseDate)
    assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseDate)
    assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseDate)
  }

  @Test
  fun `imprisonmentStatus and description are present`() {
    val prisoner = PrisonerA(
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        imprisonmentStatus = "LIFE",
        imprisonmentStatusDescription = "Serving Life Imprisonment"
      ),
      incentiveLevel = null,
      restrictedPatientData = null
    )
    assertThat(prisoner.imprisonmentStatus).isEqualTo("LIFE")
    assertThat(prisoner.imprisonmentStatusDescription).isEqualTo("Serving Life Imprisonment")
  }

  @Test
  fun `maps alerts correctly`() {
    val prisoner = PrisonerA(
      OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        alerts = listOf(
          Alert(
            alertId = 1,
            active = true,
            expired = false,
            alertCode = "x-code",
            alertType = "x-type",
            dateCreated = LocalDate.now()
          )
        )
      ),
      incentiveLevel = null,
      restrictedPatientData = null
    )

    assertThat(prisoner.alerts?.first())
      .extracting("alertType", "alertCode", "active", "expired")
      .contains("x-type", "x-code", true, false)
  }

  @Test
  internal fun `current incentive is mapped`() {
    val prisoner = PrisonerA(
      aBooking(),
      IncentiveLevel(
        iepLevel = "Standard",
        iepTime = LocalDateTime.parse("2021-01-01T11:00:00"),
        nextReviewDate = LocalDate.parse("2022-02-02"),
      ),
      restrictedPatientData = null
    )

    assertThat(prisoner.currentIncentive).isNotNull
    assertThat(prisoner.currentIncentive?.level).isNotNull
    assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
    assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
    assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
  }

  @Test
  internal fun `current incentive is mapped when there is no failure`() {
    val prisoner = PrisonerA(
      null,
      aBooking(),
      Result.success(
        IncentiveLevel(
          iepLevel = "Standard",
          iepTime = LocalDateTime.parse("2021-01-01T11:00:00"),
          nextReviewDate = LocalDate.parse("2022-02-02"),
        )
      ),
      restrictedPatientData = null
    )

    assertThat(prisoner.currentIncentive).isNotNull
    assertThat(prisoner.currentIncentive?.level).isNotNull
    assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
    assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
    assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
  }

  @Test
  internal fun `restrictive patient data is mapped`() {
    val prisoner = PrisonerA(
      aBooking().copy(locationDescription = "OUT"),
      incentiveLevel = null,
      restrictedPatientData = RestrictivePatient(
        supportingPrisonId = "MDI",
        dischargedHospital = Agency(
          agencyId = "HAZLWD",
          agencyType = "HSHOSP",
          active = true,
          description = "Hazelwood Hospital"
        ),
        dischargeDate = LocalDate.now(),
        dischargeDetails = "Getting worse"
      )
    )

    assertThat(prisoner.restrictedPatient).isTrue
    assertThat(prisoner.supportingPrisonId).isEqualTo("MDI")
    assertThat(prisoner.dischargedHospitalId).isEqualTo("HAZLWD")
    assertThat(prisoner.dischargedHospitalDescription).isEqualTo("Hazelwood Hospital")
    assertThat(prisoner.dischargeDate).isEqualTo(LocalDate.now())
    assertThat(prisoner.dischargeDetails).isEqualTo("Getting worse")
    assertThat(prisoner.locationDescription).isEqualTo("OUT - discharged to Hazelwood Hospital")
  }
  @Test
  internal fun `restrictive patient data can be null`() {
    val prisoner = PrisonerA(
      aBooking().copy(locationDescription = "OUT"),
      incentiveLevel = null,
      restrictedPatientData = null
    )

    assertThat(prisoner.restrictedPatient).isFalse
    assertThat(prisoner.supportingPrisonId).isNull()
    assertThat(prisoner.dischargedHospitalId).isNull()
    assertThat(prisoner.dischargedHospitalDescription).isNull()
    assertThat(prisoner.dischargeDate).isNull()
    assertThat(prisoner.dischargeDetails).isNull()
    assertThat(prisoner.locationDescription).isEqualTo("OUT")
  }

  @Nested
  inner class WithIncentiveLevelFailure {
    @Test
    internal fun `will fall back to old level when present`() {
      val existingPrisoner = PrisonerA(
        aBooking().copy(locationDescription = "OUT"),
        incentiveLevel = IncentiveLevel(
          iepLevel = "Standard",
          iepTime = LocalDateTime.parse("2021-01-01T11:00:00"),
          nextReviewDate = LocalDate.parse("2022-02-02"),
        ),
        restrictedPatientData = null
      )

      val prisoner = PrisonerA(
        existingPrisoner,
        aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = null
      )

      assertThat(prisoner.currentIncentive).isNotNull
      assertThat(prisoner.currentIncentive?.level).isNotNull
      assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
      assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
      assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
    }

    @Test
    internal fun `will fall back to null when previous record did not exist`() {
      val prisoner = PrisonerA(
        existingPrisoner = null,
        aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = null
      )

      assertThat(prisoner.currentIncentive).isNull()
    }

    @Test
    internal fun `will fall back to null when the previous record's incentive was null`() {
      val existingPrisoner = PrisonerA(
        aBooking().copy(locationDescription = "OUT"),
        incentiveLevel = null,
        restrictedPatientData = null
      )

      val prisoner = PrisonerA(
        existingPrisoner,
        aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = null
      )

      assertThat(prisoner.currentIncentive).isNull()
    }
  }
}

private fun aBooking() = OffenderBooking("A1234AA", "Fred", "Bloggs", LocalDate.now().minusYears(18), false)
