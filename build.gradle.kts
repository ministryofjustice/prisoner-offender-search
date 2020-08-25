plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "1.0.0"
  kotlin("plugin.spring") version "1.4.0"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.data:spring-data-elasticsearch")
  implementation("org.springframework.boot:spring-boot-devtools")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springdoc:springdoc-openapi-ui:1.4.4")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.4.4")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.4.4")
  implementation("com.nimbusds:nimbus-jose-jwt:8.20")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.apache.commons:commons-lang3:3.11")
  implementation("com.google.code.gson:gson:2.8.6")
  implementation("com.google.guava:guava:29.0-jre")

  implementation("org.springframework:spring-jms")
  implementation(platform("com.amazonaws:aws-java-sdk-bom:1.11.847"))
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.11.848")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.1")
  testImplementation("org.testcontainers:localstack:1.14.3")
  testImplementation("org.testcontainers:elasticsearch:1.14.3")
  testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
}
