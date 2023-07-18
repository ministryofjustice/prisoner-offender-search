plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.9.3"
  kotlin("plugin.spring") version "1.8.22"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("elasticsearch-suppressions.xml")
}

// SDI-260: pinned elasticsearch version to 7.12.1 and spring-data-elasticsearch:4.3.4
// rest-high-level-client:7.15.2 is not compatible with our current version of elasticsearch
// (AWS currently only support elasticsearch to 7.10)
// https://github.com/elastic/elasticsearch/issues/76091#issuecomment-892817267
ext["elasticsearch.version"] = "7.12.1"
val springDataElasticSearch by extra("4.3.4")

// still on spring boot 2
val hmppsSqsVersion by extra("1.3.0")

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.data:spring-data-elasticsearch:$springDataElasticSearch")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.7.0")
  implementation("org.springdoc:springdoc-openapi-ui:1.7.0")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.7.0")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.7.0")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.apache.commons:commons-lang3:3.12.0")
  implementation("com.google.code.gson:gson:2.10.1")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:$hmppsSqsVersion")
  implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.12.507")
  implementation("org.awaitility:awaitility-kotlin:4.2.0")

  runtimeOnly("org.postgresql:postgresql:42.6.0")
  runtimeOnly("org.flywaydb:flyway-core")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.15") // Unable to upgrade to 2.1.16 as attempting to use the parser caused a 500 from /v3/api-docs. It seems there's an incorrect dependency on an OAS3.1 type but I couldn't work out which project is wrong - so hopefully this will get sorted by the maintainers in due course.
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.0.0")
  testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "19"
    }
  }
}
