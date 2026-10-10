plugins {
    `java-library`
}

// Micronaut R2DBC 7.3.0 retains the connection factory across development restarts
// (micronaut-projects/micronaut-r2dbc#1066). Remove the override once the platform moves to it.
val micronautR2dbcDevelopment = "7.3.0-SNAPSHOT"

dependencies {
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautDataR2dbc)
    testImplementation("io.micronaut.r2dbc:micronaut-r2dbc-core:$micronautR2dbcDevelopment")
    testImplementation(mn.micronaut.dev.tck)
    // the reload harness compiles the application under test with the processors on the test classpath
    testImplementation(mn.micronaut.inject.java)
    testImplementation(projects.micronautDataProcessor)

    testImplementation(mnR2dbc.r2dbc.pool)
    testImplementation(mnR2dbc.r2dbc.h2)
    // the SQL schema of an entity is built with the Jackson annotations on the classpath
    testRuntimeOnly(mn.jackson.databind)

    testImplementation(mnTest.junit.jupiter.api)
    testImplementation(mnTest.junit.jupiter.params)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
    testImplementation(mnLogging.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}
