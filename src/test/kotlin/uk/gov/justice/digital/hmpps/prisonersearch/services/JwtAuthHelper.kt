package uk.gov.justice.digital.hmpps.prisonersearch.services

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.Date
import java.util.UUID

@Component
class JwtAuthHelper {
  private val keyPair: KeyPair

  init {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    keyPair = gen.generateKeyPair()
  }

  @Bean
  fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()

  fun createJwt(
    subject: String? = null,
    scope: List<String>? = listOf(),
    roles: List<String>? = listOf(),
    expiryTime: Duration = Duration.ofHours(1),
    jwtId: String = UUID.randomUUID().toString(),
  ): String {
    val claims = mutableMapOf<String, Any?>("client_id" to "prisoner-offender-search-client")
      .apply {
        subject?.let { this["user_name"] = subject }
        roles?.let { this["authorities"] = roles }
        scope?.let { this["scope"] = scope }
      }
    return Jwts.builder()
      .setId(jwtId)
      .setSubject(subject)
      .addClaims(claims)
      .setExpiration(Date(System.currentTimeMillis() + expiryTime.toMillis()))
      .signWith(SignatureAlgorithm.RS256, keyPair.private)
      .compact()
  }
}
