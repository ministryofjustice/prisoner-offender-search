package uk.gov.justice.digital.hmpps.prisonersearch.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.config.AuthAwareAuthenticationToken

@Component
class UserSecurityUtils : AuthenticationFacade {
  val authentication: Authentication
    get() = SecurityContextHolder.getContext().authentication

  override fun currentUsername(): String? {
    return authentication.principal as String?
  }

  override fun currentClientId(): String? {
    val auth =  authentication as AuthAwareAuthenticationToken
    return auth.clientId
  }
}
