plugins {
	java
	jacoco
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.fakubwoy"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

// Patch releases newer than what Boot 3.5.16 manages, to fix CVEs found by Trivy (see README).
// Drop each override once a Boot release ships at or above that version.
extra["tomcat.version"] = "10.1.61"
extra["jackson-bom.version"] = "2.21.8"
extra["postgresql.version"] = "42.7.14"

val otelAgent by configurations.creating

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	// Pulled in by kafka-clients, which Boot does not manage for lz4.
	runtimeOnly("at.yawk.lz4:lz4-java:1.11.4")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	// API only: the real implementation comes from the Java agent attached at runtime (see Dockerfile).
	implementation("io.opentelemetry:opentelemetry-api:1.67.0")
	otelAgent("io.opentelemetry.javaagent:opentelemetry-javaagent:2.32.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.testcontainers:kafka")
	testImplementation("org.awaitility:awaitility")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Only the executable Boot jar, so the Dockerfiles can COPY build/libs/*.jar unambiguously.
tasks.jar {
	enabled = false
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	reports {
		xml.required = true
		html.required = true
	}
}

// The Dockerfile cannot download anything, so the agent jar is fetched here and copied next to the Boot jar.
tasks.register<Copy>("copyOtelAgent") {
	from(otelAgent)
	into(layout.buildDirectory.dir("agent"))
	rename { "opentelemetry-javaagent.jar" }
}
