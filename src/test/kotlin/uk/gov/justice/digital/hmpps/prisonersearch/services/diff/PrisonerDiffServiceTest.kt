package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerAlias
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonerDiffServiceTest {

  @Nested
  inner class GetDiff {
    @Test
    fun `should find no differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "any" }
      val prisoner2 = Prisoner().apply { pncNumber = "any" }

      assertThat(getDiffResult(prisoner1, prisoner2)).isEmpty()
    }

    @Test
    fun `should report single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("pncNumber", "somePnc1", "somePnc2"))
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2" }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
          Tuple("croNumber", "someCro1", "someCro2")
        )
    }

    @Test
    fun `should report differences of different property types`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; firstName = "firstName1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; firstName = "firstName2" }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
          Tuple("firstName", "firstName1", "firstName2")
        )
    }

    @Test
    fun `should report boolean difference`() {
      val prisoner1 = Prisoner().apply { youthOffender = true }
      val prisoner2 = Prisoner().apply { youthOffender = false }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("youthOffender", true, false))
    }

    @Test
    fun `should handle null difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = null }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc" }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("pncNumber", null, "somePnc"))
    }

    @Test
    fun `should ignore null equality`() {
      val prisoner1 = Prisoner().apply { pncNumber = null }
      val prisoner2 = Prisoner().apply { pncNumber = null }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs).isEmpty()
    }

    @Test
    fun `should handle list difference`() {
      val prisoner1 = Prisoner().apply { aliases = listOf() }
      val prisoner2 = Prisoner().apply { aliases = listOf(alias(firstName = "aliasFirstName", lastName = "aliasLastName", dateOfBirth = LocalDate.now())) }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("aliases", listOf<PrisonerAlias>(), listOf(alias(firstName = "aliasFirstName", lastName = "aliasLastName", dateOfBirth = LocalDate.now()))))
    }

    fun alias(firstName: String, lastName: String, dateOfBirth: LocalDate) =
      PrisonerAlias(firstName = firstName, middleNames = null, lastName = lastName, dateOfBirth = dateOfBirth, gender = null, ethnicity = null)

    @Test
    fun `should handle LocalDate difference`() {
      val prisoner1 = Prisoner().apply { sentenceStartDate = LocalDate.of(2022, 9, 12) }
      val prisoner2 = Prisoner().apply { sentenceStartDate = LocalDate.of(2021, 8, 11) }

      assertThat(getDiffResult(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("sentenceStartDate", LocalDate.of(2022, 9, 12), LocalDate.of(2021, 8, 11)))
    }
  }

  @Nested
  inner class Groupings {
    @Test
    fun `groups properties by property type`() {
      assertThat(propertiesByDiffCategory[DiffCategory.IDENTIFIERS]).contains("pncNumber", "croNumber")
      assertThat(propertiesByDiffCategory[DiffCategory.PERSONAL_DETAILS]).contains("firstName")
    }

    @Test
    fun `maps property types by property`() {
      assertThat(diffCategoriesByProperty["pncNumber"]).isEqualTo(DiffCategory.IDENTIFIERS)
      assertThat(diffCategoriesByProperty["croNumber"]).isEqualTo(DiffCategory.IDENTIFIERS)
      assertThat(diffCategoriesByProperty["firstName"]).isEqualTo(DiffCategory.PERSONAL_DETAILS)
    }
  }

  @Nested
  inner class GetDifferencesByCategory {
    @Test
    fun `should report zero differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc"; croNumber = "someCro"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc"; croNumber = "someCro"; firstName = "someName" }

      val diffsByType = getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType).isEmpty()
    }

    @Test
    fun `should report single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro"; firstName = "someName" }

      val diffsByType = getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffCategory.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc1", "somePnc2"),
        )
    }

    @Test
    fun `should allow null differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc"; croNumber = null; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = null; croNumber = "someCro"; firstName = "someName" }

      val diffsByType = getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffCategory.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc", null),
          Tuple("croNumber", DiffCategory.IDENTIFIERS, null, "someCro"),
        )
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someName" }

      val diffsByType = getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffCategory.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", DiffCategory.IDENTIFIERS, "someCro1", "someCro2"),
        )
    }

    @Test
    fun `should report multiple differences of multiple property types`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someName1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someName2" }

      val diffsByType = getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactlyInAnyOrder(DiffCategory.IDENTIFIERS, DiffCategory.PERSONAL_DETAILS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      val personalDetailDiffs = diffsByType[DiffCategory.PERSONAL_DETAILS]

      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", DiffCategory.IDENTIFIERS, "someCro1", "someCro2")
        )
      assertThat(personalDetailDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("firstName", DiffCategory.PERSONAL_DETAILS, "someName1", "someName2"),
        )
    }

    @Test
    fun `should report alerts differences`() {
      val prisoner1 = Prisoner().apply { alerts = listOf(alert()) }
      val prisoner2 = Prisoner().apply { alerts = listOf(alert(active = false)) }

      val diffsByType = getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactlyInAnyOrder(DiffCategory.ALERTS)
      val alertsDiffs = diffsByType[DiffCategory.ALERTS]

      assertThat(alertsDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          (Tuple("alerts", DiffCategory.ALERTS, listOf(alert()), listOf(alert(active = false))))
        )
    }

    private fun alert(alertType: String = "SOME_TYPE", alertCode: String = "SOME_CODE", active: Boolean = true, expired: Boolean = false) =
      PrisonerAlert(alertType, alertCode, active, expired)
  }

  @Nested
  inner class RaiseDifferencesTelemetry {
    private val telemetryClient = mock<TelemetryClient>()

    @Test
    fun `should report identifiers`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      raiseDifferencesTelemetry(
        "someOffenderNo",
        "someBookingNo",
        getDifferencesByCategory(prisoner1, prisoner2),
        telemetryClient
      )

      verify(telemetryClient).trackEvent(
        eq("POSPrisonerUpdated"),
        check<Map<String, String>> {
          assertThat(LocalDateTime.parse(it["processedTime"]).toLocalDate()).isEqualTo(LocalDate.now())
          assertThat(it["offenderNumber"]).isEqualTo("someOffenderNo")
          assertThat(it["bookingNumber"]).isEqualTo("someBookingNo")
        },
        isNull()
      )
    }

    @Test
    fun `should report a single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      raiseDifferencesTelemetry(
        "someOffenderNo",
        "someBookingNo",
        getDifferencesByCategory(prisoner1, prisoner2),
        telemetryClient
      )

      verify(telemetryClient).trackEvent(
        eq("POSPrisonerUpdated"),
        check<Map<String, String>> {
          assertThat(it["categoryChanged"]).isEqualTo(DiffCategory.IDENTIFIERS.name)
          assertThat(it["pncNumber"]).isEqualTo("somePnc1 -> somePnc2")
        },
        isNull()
      )
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2" }

      raiseDifferencesTelemetry(
        "someOffenderNo",
        "someBookingNo",
        getDifferencesByCategory(prisoner1, prisoner2),
        telemetryClient
      )

      verify(telemetryClient).trackEvent(
        eq("POSPrisonerUpdated"),
        check<Map<String, String>> {
          assertThat(it["categoryChanged"]).isEqualTo(DiffCategory.IDENTIFIERS.name)
          assertThat(it["pncNumber"]).isEqualTo("somePnc1 -> somePnc2")
          assertThat(it["croNumber"]).isEqualTo("someCro1 -> someCro2")
        },
        isNull()
      )
    }

    @Test
    fun `should report null differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc"; croNumber = null }
      val prisoner2 = Prisoner().apply { pncNumber = null; croNumber = "someCro" }

      raiseDifferencesTelemetry(
        "someOffenderNo",
        "someBookingNo",
        getDifferencesByCategory(prisoner1, prisoner2),
        telemetryClient
      )

      verify(telemetryClient).trackEvent(
        eq("POSPrisonerUpdated"),
        check<Map<String, String>> {
          assertThat(it["categoryChanged"]).isEqualTo(DiffCategory.IDENTIFIERS.name)
          assertThat(it["pncNumber"]).isEqualTo("somePnc -> null")
          assertThat(it["croNumber"]).isEqualTo("null -> someCro")
        },
        isNull()
      )
    }

    @Test
    fun `should report multiple differences of multiple types`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someFirstName1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someFirstName2" }

      raiseDifferencesTelemetry(
        "someOffenderNo",
        "someBookingNo",
        getDifferencesByCategory(prisoner1, prisoner2),
        telemetryClient
      )

      verify(telemetryClient)
        .trackEvent(
          eq("POSPrisonerUpdated"),
          check<Map<String, String>> {
            assertThat(it["categoryChanged"]).isEqualTo(DiffCategory.IDENTIFIERS.name)
            assertThat(it["pncNumber"]).isEqualTo("somePnc1 -> somePnc2")
            assertThat(it["croNumber"]).isEqualTo("someCro1 -> someCro2")
          },
          isNull()
        )

      verify(telemetryClient)
        .trackEvent(
          eq("POSPrisonerUpdated"),
          check<Map<String, String>> {
            assertThat(it["categoryChanged"]).isEqualTo(DiffCategory.PERSONAL_DETAILS.name)
            assertThat(it["firstName"]).isEqualTo("someFirstName1 -> someFirstName2")
          },
          isNull()
        )
    }
  }
}
