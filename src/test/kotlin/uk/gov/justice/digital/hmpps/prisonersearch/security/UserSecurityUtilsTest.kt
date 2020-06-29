package uk.gov.justice.digital.hmpps.prisonersearch.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import uk.gov.justice.digital.hmpps.prisonersearch.config.AuthAwareAuthenticationToken

class UserSecurityUtilsTest {
  private val userSecurityUtils: UserSecurityUtils = UserSecurityUtils()

  @Test
  fun userAuthenticationCurrentUsername() {
    setAuthentication()
    assertThat(userSecurityUtils.currentUsername()).isEqualTo("UserName")
  }

  @Test
  fun userAuthenticationClientID() {
    setAuthentication()
    assertThat(userSecurityUtils.currentClientId()).isEqualTo("clientID")
  }

  private fun setAuthentication() {
    val auth: Authentication = AuthAwareAuthenticationToken(
      Mockito.mock(Jwt::class.java),
      "UserName",
      "clientID",
      emptySet()
    )
    SecurityContextHolder.getContext().authentication = auth
  }
}
