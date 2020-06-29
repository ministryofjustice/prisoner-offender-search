package uk.gov.justice.digital.hmpps.prisonersearch.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.config.AuthAwareAuthenticationToken

@Component
class UserSecurityUtils : AuthenticationFacade {
  val authentication: Authentication
    get() = SecurityContextHolder.getContext().authentication

  override fun currentUsername(): String? {
    val username: String?
    val userPrincipal = userPrincipal
    username = when (userPrincipal) {
      is String -> {
        userPrincipal
      }
      is UserDetails -> {
        userPrincipal.username
      }
      is Map<*, *> -> {
        (userPrincipal["username"] as String?)!!
      }
      else -> {
        null
      }
    }
    return username
  }

  override fun currentClientId(): String? {
    val auth =  authentication as AuthAwareAuthenticationToken
    return auth.clientId
  }

  private val userPrincipal: Any?
    get() {
      var userPrincipal: Any? = null
      val auth = authentication
      userPrincipal = auth.principal
      return userPrincipal
    }
}
