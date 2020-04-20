package uk.org.justice.digital.hmpps.prisonersearch.config


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.Contact
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2
import uk.org.justice.digital.hmpps.prisonersearch.resource.PrisonerSearchResource
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

@Configuration
@EnableWebSecurity
@EnableSwagger2
@EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
open class ResourceServerConfiguration : WebSecurityConfigurerAdapter() {

    @Autowired(required = false)
    private val buildProperties: BuildProperties? = null

    /**
     * @return health data. Note this is unsecured so no sensitive data allowed!
     */
    private val version: String
        get() = if (buildProperties == null) "version not available" else buildProperties.version

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {

        http.headers().frameOptions().sameOrigin().and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                // Can't have CSRF protection as requires session
                .and().csrf().disable()
                .authorizeRequests { auth ->
                    auth.antMatchers(
                                    "/webjars/**", "/favicon.ico", "/csrf",
                                    "/health", "/info", "/health/ping",
                                    "/v2/api-docs",
                                    "/swagger-ui.html", "/swagger-resources", "/swagger-resources/configuration/ui",
                                    "/swagger-resources/configuration/security",
                        "/prisoner-search/**"
                            ).permitAll()
                            .anyRequest()
                            .authenticated()
                }.oauth2ResourceServer().jwt().jwtAuthenticationConverter(AuthAwareTokenConverter())

    }

    @Bean
    open fun api(): Docket {
        val apiInfo = ApiInfo("Prisoner Offender Search", "API for providing Indexing and searching of offenders",
                version, "", Contact("HMPPS Digital Studio", "", "feedback@digital.justice.gov.uk"),
                "", "", emptyList())
        val docket = Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .apiInfo(apiInfo)
                .select()
                .apis(RequestHandlerSelectors.basePackage(PrisonerSearchResource::class.java.getPackage().getName()))
                .paths(PathSelectors.any())
                .build()
        docket.genericModelSubstitutes(Optional::class.java)
        docket.directModelSubstitute(ZonedDateTime::class.java, Date::class.java)
        docket.directModelSubstitute(LocalDateTime::class.java, Date::class.java)
        return docket
    }
}
