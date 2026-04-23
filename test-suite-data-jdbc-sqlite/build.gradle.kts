plugins {
    `java-library`
}

dependencies {
    // DI
    testAnnotationProcessor(mn.micronaut.inject.java)

    // SQLite
    testRuntimeOnly(mnSql.sqlite.jdbc)

    // CONNECTION POOL
//    testRuntimeOnly(mnSql.micronaut.jdbc.hikari)
    testRuntimeOnly(mnSql.micronaut.jdbc.tomcat)

    // MULTITENANCY
    testImplementation(mnMultitenancy.micronaut.multitenancy)

    // REACTIVE
    testImplementation(mnReactor.micronaut.reactor)
    testImplementation(mnRxjava2.micronaut.rxjava2)

    // TEST
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mnTest.micronaut.test.junit5)

    // PERSISTENCE API
    testImplementation(mnSql.jakarta.persistence.api)
    testImplementation(libs.managed.javax.persistence.api)
    testImplementation(libs.managed.jakarta.data.api)

    // DATA
    testAnnotationProcessor(projects.micronautDataProcessor)
    testImplementation(projects.micronautDataJdbc)
    testImplementation(projects.micronautDataTck)

    // HTTP Client
    testImplementation(mn.micronaut.http.client)

    // Validation
    testImplementation(mnValidation.micronaut.validation)
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)

    // Serialization
    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testImplementation(mnSerde.micronaut.serde.jackson)

    // LOGGING
    runtimeOnly(mnLogging.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}
