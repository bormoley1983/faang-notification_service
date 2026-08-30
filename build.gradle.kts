plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "faang.school"
version = "1.0"

val javaVersion = 25
val springCloudVersion = "2025.1.3"
val testcontainersVersion = "2.0.5"
val mapstructVersion = "1.6.3"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
    }
}

dependencies {
    /**
     * Spring boot starters
     */
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    /**
     * Utils & Logging
     */
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind")

    /**
     * Vonage
     */
    implementation("com.vonage:client:7.3.0")

    /**
     * Telegram bot
     */
    implementation("org.telegram:telegrambots-spring-boot-starter:6.9.7.1")

    /**
     * Tests
     */
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-Xshare:off", "-javaagent:${mockitoAgent.asPath}")
}

// Unit tests only — integration tests are excluded by tag and run via `integrationTest`.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}

// Integration tests (Testcontainers) — run explicitly, not part of the unit gate.
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (tagged 'integration')."
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
    }
}

// Coverage gate for application logic only. Documented exclusions (narrow, per rules):
// NotificationServiceApp (bootstrap), config.* (bean wiring / property holders),
// model.* (Lombok DTOs, enums, event records with no custom behavior),
// exception.* (exception classes with no logic), client.UserServiceClient (Feign interface).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf(
                "faang.school.notificationservice.listener.*",
                "faang.school.notificationservice.service.*",
                "faang.school.notificationservice.bot.*",
                "faang.school.notificationservice.controller.*",
                "faang.school.notificationservice.config.context.*",
                "faang.school.notificationservice.client.FeignUserInterceptor"
            )
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.bootJar {
    archiveFileName.set("service.jar")
}
