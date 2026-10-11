plugins {
    `java-library`
}

dependencies {
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautDataJdbc)
    testImplementation(mn.micronaut.dev.tck)
    // the reload harness compiles the application under test with the processors on the test classpath
    testImplementation(mn.micronaut.inject.java)
    testImplementation(projects.micronautDataProcessor)

    testRuntimeOnly(mnSql.micronaut.jdbc.hikari)
    testRuntimeOnly(mnSql.h2)

    testImplementation(mnTest.junit.jupiter.api)
    testImplementation(mnTest.junit.jupiter.params)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
    testImplementation(mnLogging.logback.classic)
}

tasks.test {
    useJUnitPlatform()
    // the schema generator finds the entities with BeanIntrospector.SHARED, which sees the reloadable tier of the
    // development runtime only through the context class loader; the runtime does not set this yet
    systemProperty("micronaut.introspections.use.context.classloader", "true")
}
