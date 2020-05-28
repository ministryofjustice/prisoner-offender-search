package uk.gov.justice.digital.hmpps.prisonersearch.services.health


import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.services.IndexStatusService
import uk.gov.justice.digital.hmpps.prisonersearch.services.PrisonerIndexService


@Component
class IndexInfo(private val indexStatusService : IndexStatusService,
                private val prisonerIndexService: PrisonerIndexService) : InfoContributor {

  override fun contribute(builder : Info.Builder) {
    val indexStatus = indexStatusService.getCurrentIndex()
    builder.withDetail("index-status", indexStatus);
    builder.withDetail("index-size", mapOf(
      indexStatus.currentIndex.name to prisonerIndexService.countIndex(indexStatus.currentIndex),
      indexStatus.currentIndex.otherIndex().name to prisonerIndexService.countIndex(indexStatus.currentIndex.otherIndex())
    ))
  }

}
