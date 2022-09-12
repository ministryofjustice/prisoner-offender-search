package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerAlias
import java.time.LocalDate

class PrisonerDiffServiceTest {

  @Nested
  inner class GetDiff {
    @Test
    fun `should find no differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "any" }
      val prisoner2 = Prisoner().apply { pncNumber = "any" }

      assertThat(getDiff(prisoner1, prisoner2)).isEmpty()
    }

    @Test
    fun `should report single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      assertThat(getDiff(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("pncNumber", "somePnc1", "somePnc2"))
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2" }

      assertThat(getDiff(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
          Tuple("croNumber", "someCro1", "someCro2")
        )
    }

    @Test
    fun `should report differences of different types`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; firstName = "firstName1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; firstName = "firstName2" }

      assertThat(getDiff(prisoner1, prisoner2).diffs)
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

      assertThat(getDiff(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("youthOffender", true, false))
    }

    @Test
    fun `should handle null difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = null }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc" }

      assertThat(getDiff(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("pncNumber", null, "somePnc"))
    }

    @Test
    fun `should ignore null equality`() {
      val prisoner1 = Prisoner().apply { pncNumber = null }
      val prisoner2 = Prisoner().apply { pncNumber = null }

      assertThat(getDiff(prisoner1, prisoner2).diffs).isEmpty()
    }

    @Test
    fun `should handle list difference`() {
      val prisoner1 = Prisoner().apply { aliases = listOf() }
      val prisoner2 = Prisoner().apply { aliases = listOf(alias(firstName = "aliasFirstName", lastName = "aliasLastName", dateOfBirth = LocalDate.now())) }

      assertThat(getDiff(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("aliases", listOf<PrisonerAlias>(), listOf(alias(firstName = "aliasFirstName", lastName = "aliasLastName", dateOfBirth = LocalDate.now()))))
    }

    fun alias(firstName: String, lastName: String, dateOfBirth: LocalDate) =
      PrisonerAlias(firstName = firstName, middleNames = null, lastName = lastName, dateOfBirth = dateOfBirth, gender = null, ethnicity = null)

    @Test
    fun `should handle LocalDate difference`() {
      val prisoner1 = Prisoner().apply { sentenceStartDate = LocalDate.of(2022, 9, 12) }
      val prisoner2 = Prisoner().apply { sentenceStartDate = LocalDate.of(2021, 8, 11) }

      assertThat(getDiff(prisoner1, prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("sentenceStartDate", LocalDate.of(2022, 9, 12), LocalDate.of(2021, 8, 11)))
    }
  }

  @Nested
  inner class Groupings {
    @Test
    fun `groups prisoner class members by DiffType`() {
      assertThat(propertiesByDiffType[DiffType.IDENTIFIERS]).contains("pncNumber", "croNumber")
      assertThat(propertiesByDiffType[DiffType.PERSONAL_DETAILS]).contains("firstName")
    }
    @Test
    fun `calculates diff types by property name`() {
      assertThat(diffTypesByProperty["pncNumber"]).isEqualTo(DiffType.IDENTIFIERS)
      assertThat(diffTypesByProperty["croNumber"]).isEqualTo(DiffType.IDENTIFIERS)
      assertThat(diffTypesByProperty["firstName"]).isEqualTo(DiffType.PERSONAL_DETAILS)
    }
  }

  @Nested
  inner class GroupDiffsByType {
    @Test
    fun `should report zero differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc"; croNumber = "someCro"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc"; croNumber = "someCro"; firstName = "someName" }

      val diffsByType = getDifferencesByType(prisoner1, prisoner2)

      assertThat(diffsByType).isEmpty()
    }

    @Test
    fun `should report single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro"; firstName = "someName" }

      val diffsByType = getDifferencesByType(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffType.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffType.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "diffType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffType.IDENTIFIERS, "somePnc1", "somePnc2"),
        )
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someName" }

      val diffsByType = getDifferencesByType(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffType.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffType.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "diffType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffType.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", DiffType.IDENTIFIERS, "someCro1", "someCro2"),
        )
    }

    @Test
    fun `should report multiple differences of multiple types`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someName1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someName2" }

      val diffsByType = getDifferencesByType(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactlyInAnyOrder(DiffType.IDENTIFIERS, DiffType.PERSONAL_DETAILS)
      val identifierDiffs = diffsByType[DiffType.IDENTIFIERS]
      val personalDetailDiffs = diffsByType[DiffType.PERSONAL_DETAILS]

      assertThat(identifierDiffs)
        .extracting("property", "diffType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffType.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", DiffType.IDENTIFIERS, "someCro1", "someCro2")
        )
      assertThat(personalDetailDiffs)
        .extracting("property", "diffType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("firstName", DiffType.PERSONAL_DETAILS, "someName1", "someName2"),
        )
    }
  }
}
