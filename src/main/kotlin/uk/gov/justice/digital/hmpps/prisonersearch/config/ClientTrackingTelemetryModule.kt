package uk.gov.justice.digital.hmpps.prisonersearch.config

import com.microsoft.applicationinsights.TelemetryConfiguration
import com.microsoft.applicationinsights.extensibility.TelemetryModule
import com.microsoft.applicationinsights.web.extensibility.modules.WebTelemetryModule
import com.microsoft.applicationinsights.web.internal.ThreadContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import java.text.ParseException
import java.util.Optional
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest

@Configuration
class ClientTrackingTelemetryModule : WebTelemetryModule, TelemetryModule {
  override fun onBeginRequest(req: ServletRequest, res: ServletResponse) {
    val httpServletRequest = req as HttpServletRequest
    val token = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)
    val bearer = "Bearer "
    if (StringUtils.startsWithIgnoreCase(token, bearer)) {
      try {
        val jwtBody = getClaimsFromJWT(token)
        val properties = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
        val user = Optional.ofNullable(jwtBody.getClaim("user_name"))
        user.map { value: Any -> value.toString() }.ifPresent { u: String -> properties["username"] = u }
        properties["clientId"] = jwtBody.getClaim("client_id").toString()
      } catch (e: ParseException) {
        ClientTrackingTelemetryModule.log.warn("problem decoding jwt public key for application insights", e)
      }
    }
  }

  @Throws(ParseException::class)
  private fun getClaimsFromJWT(token: String): JWTClaimsSet {
    val signedJWT = SignedJWT.parse(token.replace("Bearer ", ""))
    return signedJWT.jwtClaimsSet
  }

  override fun onEndRequest(req: ServletRequest, res: ServletResponse) {}
  override fun initialize(configuration: TelemetryConfiguration) {}

  companion object {
    val log: Logger = LoggerFactory.getLogger(ClientTrackingTelemetryModule::class.java)
  }
}
