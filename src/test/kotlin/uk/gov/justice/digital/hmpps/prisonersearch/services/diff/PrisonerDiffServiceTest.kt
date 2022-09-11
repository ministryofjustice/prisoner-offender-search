package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner

class PrisonerDiffServiceTest {

  @Nested
  inner class CreateDiff {
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
  }

  @Nested
  inner class GroupDiffsByType {
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
        .extracting("property", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
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
        .extracting("property", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
          Tuple("croNumber", "someCro1", "someCro2"),
        )
    }

    @Test
    fun `should report multiple differences of multiple types`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someName1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someName2" }

      val diffsByType = getDifferencesByType(prisoner1, prisoner2)

      val identifierDiffs = diffsByType[DiffType.IDENTIFIERS]
      val personalDetailDiffs = diffsByType[DiffType.PERSONAL_DETAILS]

      assertThat(identifierDiffs)
        .extracting("property", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
          Tuple("croNumber", "someCro1", "someCro2")
        )
      assertThat(personalDetailDiffs)
        .extracting("property", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("firstName", "someName1", "someName2"),
        )
    }
  }
}
