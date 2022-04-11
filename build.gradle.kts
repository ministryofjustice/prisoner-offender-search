plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.1.3"
  kotlin("plugin.spring") version "1.6.20"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("elasticsearch-suppressions.xml")
}

// pinned elasticsearch version to 7.12.1 and spring-data-elasticsearch:4.2.7
// rest-high-level-client:7.15.2 is not compatible with our current version of elasticsearch
// (AWS currently only support elasticsearch to 7.10)
// https://github.com/elastic/elasticsearch/issues/76091#issuecomment-892817267

ext["elasticsearch.version"] = "7.12.1"

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.data:spring-data-elasticsearch:4.3.3")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.6.7")
  implementation("org.springdoc:springdoc-openapi-ui:1.6.7")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.7")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.7")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.apache.commons:commons-lang3:3.12.0")
  implementation("com.google.code.gson:gson:2.9.0")
  implementation("com.google.guava:guava:31.1-jre")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.3")
  implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.12.188")
  implementation("org.awaitility:awaitility-kotlin:4.2.0")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.0.32")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.mockito:mockito-inline:4.4.0")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }
}
