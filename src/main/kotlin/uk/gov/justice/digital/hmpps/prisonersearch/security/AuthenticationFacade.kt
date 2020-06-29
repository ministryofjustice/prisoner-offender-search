package uk.gov.justice.digital.hmpps.prisonersearch.security

interface AuthenticationFacade {
  fun currentUsername(): String?

  fun currentClientId(): String?
}
