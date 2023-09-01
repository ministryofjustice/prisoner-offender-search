package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.hibernate.Hibernate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Repository
interface PrisonerDifferencesRepository : JpaRepository<PrisonerDifferences, UUID> {
  fun findByNomsNumber(nomsNumber: String): List<PrisonerDifferences>
  fun findByDateTimeBetween(from: Instant, to: Instant): List<PrisonerDifferences>
  fun deleteByDateTimeBefore(to: Instant): Int
}

@Entity
@Table(name = "prisoner_differences")
class PrisonerDifferences(
  @Id
  @GeneratedValue
  val prisonerDifferencesId: UUID? = null,
  val nomsNumber: String,
  val differences: String,
  val dateTime: Instant = Instant.now(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    return prisonerDifferencesId == (other as PrisonerDifferences).prisonerDifferencesId
  }

  override fun hashCode(): Int = prisonerDifferencesId?.hashCode() ?: 0

  override fun toString(): String =
    "PrisonerDifferences(prisonerDifferencesId=$prisonerDifferencesId, nomsNumber='$nomsNumber', differences='$differences', dateTime=$dateTime)"
}
