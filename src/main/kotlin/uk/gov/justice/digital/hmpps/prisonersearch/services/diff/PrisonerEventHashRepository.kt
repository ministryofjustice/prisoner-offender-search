package uk.gov.justice.digital.hmpps.prisonersearch.services.diff

import org.hibernate.Hibernate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/*
 * This table is used to keep track of the last prisoner inserted/updated event to prevent duplicate events from being published.
 *
 * It does this by recording the hash of the Prisoner object the last time the event was sent.
 *
 * The method `upsertPrisonerEventHashIfChanged` returns 1 if the hash is new indicating the prisoner event has not yet been sent. It returns 0 if the hash already exists indicating the prisoner event has already been sent.
 */
@Repository
interface PrisonerEventHashRepository : JpaRepository<PrisonerEventHash, String> {
  /*
   * The strange syntax is how Postgres handles an upsert - if there is a conflict on the insert the update is run instead.
   */
  @Modifying
  @Query(
    value = "INSERT INTO prisoner_event_hashes (noms_number, prisoner_hash, updated_date_time) VALUES (:nomsNumber, :prisonerHash, :updatedDateTime) " +
      "ON CONFLICT (noms_number) DO UPDATE " +
      "SET prisoner_hash=:prisonerHash, updated_date_time=:updatedDateTime WHERE prisoner_event_hashes.prisoner_hash<>:prisonerHash",
    nativeQuery = true,
  )
  fun upsertPrisonerEventHashIfChanged(nomsNumber: String, prisonerHash: String, updatedDateTime: Instant): Int
}

@Entity
@Table(name = "prisoner_event_hashes")
data class PrisonerEventHash(
  @Id
  val nomsNumber: String = "",
  @Column(name = "prisoner_hash")
  val prisonerHash: String = "",
  val updatedDateTime: Instant = Instant.now(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as PrisonerEventHash

    return nomsNumber == other.nomsNumber
  }

  override fun hashCode(): Int = nomsNumber.hashCode()

  override fun toString(): String {
    return "PrisonerSentEvent(nomsNumber=$nomsNumber, prisonerHash='$prisonerHash', updatedDateTime=$updatedDateTime)"
  }
}
