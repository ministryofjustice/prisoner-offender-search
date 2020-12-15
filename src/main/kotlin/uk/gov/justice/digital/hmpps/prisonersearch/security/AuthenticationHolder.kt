package uk.gov.justice.digital.hmpps.prisonersearch.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.config.AuthAwareAuthenticationToken

@Component
class AuthenticationHolder {
  val authentication: Authentication
    get() = SecurityContextHolder.getContext().authentication

  fun currentUsername(): String? {
    val auth = authentication as AuthAwareAuthenticationToken
    return auth.userName
  }

  fun currentClientId(): String? {
    val auth = authentication as AuthAwareAuthenticationToken
    return auth.clientId
  }
}
