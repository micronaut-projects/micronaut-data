plugins {
    `java-library`
}

// Micronaut MongoDB 6.3.0 carries the development support that retains the driver clients across restarts
// (micronaut-projects/micronaut-mongodb#1011). Remove the override once the platform moves to it.
val micronautMongoDevelopment = "6.3.0"

dependencies {
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautDataMongodb)
    testImplementation("io.micronaut.mongodb:micronaut-mongo-sync:$micronautMongoDevelopment")
    testImplementation("io.micronaut.mongodb:micronaut-mongo-reactive:$micronautMongoDevelopment")
    testImplementation(mnMongo.mongo.driver)
    testImplementation(mn.micronaut.dev.tck)
    // the reload harness compiles the application under test with the processors on the test classpath
    testImplementation(mn.micronaut.inject.java)
    testImplementation(projects.micronautDataDocumentProcessor)

    testImplementation(mnTestResources.testcontainers.mongodb)

    testImplementation(mnTest.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
    testImplementation(mnLogging.logback.classic)
}

tasks.test {
    useJUnitPlatform()
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
