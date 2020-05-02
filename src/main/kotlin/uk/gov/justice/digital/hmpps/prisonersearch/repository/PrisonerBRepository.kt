package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerB

@Repository
interface PrisonerBRepository : PrisonerRepository<PrisonerB, String>