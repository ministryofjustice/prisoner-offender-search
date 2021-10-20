plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.3.10"
  kotlin("plugin.spring") version "1.5.31"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("elasticsearch-suppressions.xml")
}

// Mitigate CVE-2021-22145 - this can be removed once spring-data-elasticsearch's transitive dependency is at this version
extra["elasticsearch.version"] = "7.13.4"

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.data:spring-data-elasticsearch")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.5.11")
  implementation("org.springdoc:springdoc-openapi-ui:1.5.11")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.11")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.5.11")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.apache.commons:commons-lang3:3.12.0")
  implementation("com.google.code.gson:gson:2.8.8")
  implementation("com.google.guava:guava:31.0.1-jre")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.0.2")
  implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.12.91")
  implementation("org.awaitility:awaitility-kotlin:4.1.0")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
  testImplementation("org.mockito:mockito-inline:4.0.0")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(16))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "16"
    }
  }
}
