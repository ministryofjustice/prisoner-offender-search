import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.owasp.dependencycheck.reporting.ReportGenerator.Format.ALL
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import java.net.InetAddress
import java.time.Instant
import java.time.LocalDate

import java.time.format.DateTimeFormatter.ISO_DATE

plugins {
    kotlin("jvm") version "1.3.71"
    kotlin("plugin.spring") version "1.3.71"
    id("org.springframework.boot") version "2.2.6.RELEASE"
    id("io.spring.dependency-management") version "1.0.9.RELEASE"
    id("org.owasp.dependencycheck") version "5.3.2.1"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("com.gorylenko.gradle-git-properties") version "2.2.2"
    id("se.patrikerdes.use-latest-versions") version "0.2.13"
}

group = "uk.gov.justice.digital.hmpps"

repositories {
    mavenLocal()
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencyCheck {
    failBuildOnCVSS = 5f
    suppressionFiles = listOf("dependency-check-suppress-spring.xml")
    format = ALL
    analyzers.assemblyEnabled = false
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}

group = "uk.gov.justice.digital.hmpps"

val todaysDate: String = LocalDate.now().format(ISO_DATE)
version = if (System.getenv().contains("BUILD_NUMBER")) System.getenv("BUILD_NUMBER") else todaysDate

springBoot {
    buildInfo {
        properties {
            time = Instant.now()
            additional = mapOf(
                "by" to System.getProperty("user.name"),
                "operatingSystem" to "${System.getProperty("os.name")} (${System.getProperty("os.version")})",
                "machine" to InetAddress.getLocalHost().hostName
            )
        }
    }
}

extra["spring-security.version"] = "5.3.1.RELEASE" // Updated since spring-boot-starter-oauth2-resource-server-2.2.5.RELEASE only pulls in 5.2.2.RELEASE (still affected by CVE-2018-1258 though)

dependencyManagement {
    imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) }
}

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.data:spring-data-elasticsearch")
    implementation("org.elasticsearch:elasticsearch:6.7.2")
    implementation("org.elasticsearch.client:elasticsearch-rest-high-level-client:6.7.2")
    implementation("org.elasticsearch.client:elasticsearch-rest-client:6.7.2")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    implementation("org.springframework:spring-webflux")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-reactor-netty")

    implementation("net.logstash.logback:logstash-logback-encoder:6.3")
    implementation("com.microsoft.azure:applicationinsights-spring-boot-starter:2.6.0")
    implementation("com.microsoft.azure:applicationinsights-logging-logback:2.6.0")
    implementation("com.github.timpeeters:spring-boot-graceful-shutdown:2.2.1")

    implementation("io.springfox:springfox-swagger2:2.9.2")
    implementation("io.springfox:springfox-swagger-ui:2.9.2")
    implementation("io.springfox:springfox-bean-validators:2.9.2")
    implementation("com.nimbusds:nimbus-jose-jwt:8.11")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation( "com.google.code.gson:gson:2.8.6")
    implementation("com.google.guava:guava:28.2-jre")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework:spring-jms")
    implementation( platform ("com.amazonaws:aws-java-sdk-bom:1.11.750"))
    implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("org.junit.vintage", "junit-vintage-engine")
    }

    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("com.github.tomakehurst:wiremock-standalone:2.26.3")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("org.testcontainers:localstack:1.14.0")
    testImplementation("org.awaitility:awaitility-kotlin:4.0.2")
    testImplementation("io.jsonwebtoken:jjwt:0.9.1")
}

tasks {
    test { useJUnitPlatform() }

    val agentDeps by configurations.register("agentDeps") {
        dependencies {
            "agentDeps"("com.microsoft.azure:applicationinsights-agent:2.6.0") {
                isTransitive = false
            }
        }
    }

    val copyAgent by registering(Copy::class) {
        from(agentDeps)
        into("$buildDir/libs")
    }

    assemble { dependsOn(copyAgent) }

    bootJar {
        manifest {
            attributes("Implementation-Version" to rootProject.version, "Implementation-Title" to rootProject.name)
        }
    }
}
