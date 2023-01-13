plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.0.0-beta-4"
  kotlin("plugin.spring") version "1.8.0"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("elasticsearch-suppressions.xml")
}

repositories {
  maven { url = uri("https://repo.spring.io/milestone") }
  mavenCentral()
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.data:spring-data-elasticsearch:5.0.0")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.6.14")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.apache.commons:commons-lang3:3.12.0")
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("com.google.guava:guava:31.1-jre")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.0.0-beta-7")
  implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.12.382")
  implementation("org.awaitility:awaitility-kotlin:4.2.0")

  runtimeOnly("org.postgresql:postgresql:42.5.1")
  runtimeOnly("org.flywaydb:flyway-core")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.10")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.11.5")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.11.5")
  testImplementation("org.mockito:mockito-inline:4.11.0")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.36.0")
  testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.22.0")

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(18))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "18"
    }
  }
}
