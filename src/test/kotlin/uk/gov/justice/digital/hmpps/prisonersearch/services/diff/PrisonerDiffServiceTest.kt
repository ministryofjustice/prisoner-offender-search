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
      assertThat(propertiesByPropertyType[PropertyType.IDENTIFIERS]).contains("pncNumber", "croNumber")
      assertThat(propertiesByPropertyType[PropertyType.PERSONAL_DETAILS]).contains("firstName")
    }
    @Test
    fun `maps property types by property`() {
      assertThat(propertyTypesByProperty["pncNumber"]).isEqualTo(PropertyType.IDENTIFIERS)
      assertThat(propertyTypesByProperty["croNumber"]).isEqualTo(PropertyType.IDENTIFIERS)
      assertThat(propertyTypesByProperty["firstName"]).isEqualTo(PropertyType.PERSONAL_DETAILS)
    }
  }

  @Nested
  inner class GetDifferencesByPropertyType {
    @Test
    fun `should report zero differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc"; croNumber = "someCro"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc"; croNumber = "someCro"; firstName = "someName" }

      val diffsByType = getDifferencesByPropertyType(prisoner1, prisoner2)

      assertThat(diffsByType).isEmpty()
    }

    @Test
    fun `should report single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro"; firstName = "someName" }

      val diffsByType = getDifferencesByPropertyType(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(PropertyType.IDENTIFIERS)
      val identifierDiffs = diffsByType[PropertyType.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "propertyType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", PropertyType.IDENTIFIERS, "somePnc1", "somePnc2"),
        )
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someName" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someName" }

      val diffsByType = getDifferencesByPropertyType(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(PropertyType.IDENTIFIERS)
      val identifierDiffs = diffsByType[PropertyType.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "propertyType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", PropertyType.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", PropertyType.IDENTIFIERS, "someCro1", "someCro2"),
        )
    }

    @Test
    fun `should report multiple differences of multiple property types`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1"; croNumber = "someCro1"; firstName = "someName1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2"; croNumber = "someCro2"; firstName = "someName2" }

      val diffsByType = getDifferencesByPropertyType(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactlyInAnyOrder(PropertyType.IDENTIFIERS, PropertyType.PERSONAL_DETAILS)
      val identifierDiffs = diffsByType[PropertyType.IDENTIFIERS]
      val personalDetailDiffs = diffsByType[PropertyType.PERSONAL_DETAILS]

      assertThat(identifierDiffs)
        .extracting("property", "propertyType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", PropertyType.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", PropertyType.IDENTIFIERS, "someCro1", "someCro2")
        )
      assertThat(personalDetailDiffs)
        .extracting("property", "propertyType", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("firstName", PropertyType.PERSONAL_DETAILS, "someName1", "someName2"),
        )
    }
  }
}
