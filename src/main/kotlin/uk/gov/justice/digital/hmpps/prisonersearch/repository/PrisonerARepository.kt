package uk.gov.justice.digital.hmpps.prisonersearch.repository

import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.model.PrisonerA

@Repository
interface PrisonerARepository : PrisonerRepository<PrisonerA, String>