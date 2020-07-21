import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.jetbrains.kotlin.gradle.tasks.*

val developmentOnly: Configuration by configurations.creating
val kotlinVersion: String by project
val micronautVersion: String by project
val micronautDataVersion: String by project
val spekVersion: String by project

plugins {
    val kotlinVersion = "1.3.31"
    application
    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.kapt") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
}

version = "0.1"
group = "example"

repositories {
    maven("https://jcenter.bintray.com")
}

configurations {
    developmentOnly
}

dependencies {
    implementation(enforcedPlatform("io.micronaut:micronaut-bom:$micronautVersion"))
    compileOnly(enforcedPlatform("io.micronaut:micronaut-bom:$micronautVersion"))
    annotationProcessor(enforcedPlatform("io.micronaut:micronaut-bom:$micronautVersion"))
    testAnnotationProcessor(enforcedPlatform("io.micronaut:micronaut-bom:$micronautVersion"))
    kapt(enforcedPlatform("io.micronaut:micronaut-bom:$micronautVersion"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.micronaut.data:micronaut-data-processor:$micronautDataVersion")
    kaptTest("io.micronaut:micronaut-inject-java")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.1")
    implementation("jakarta.persistence:jakarta.persistence-api:2.2.2")
    implementation("io.micronaut.data:micronaut-data-jdbc:$micronautDataVersion")
    runtimeOnly("ch.qos.logback:logback-classic:1.2.3")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micronaut.sql:micronaut-jdbc-hikari:$micronautSqlVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")
    testImplementation("io.micronaut.test:micronaut-test-junit5:$micronautTestVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClassName = "example.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

allOpen {
    annotation("io.micronaut.aop.Around")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            javaParameters = true
        }
    }

    withType<Test> {
        classpath = classpath.plus(configurations["developmentOnly"])
        useJUnitPlatform()
    }

    named<JavaExec>("run") {
        doFirst {
            jvmArgs = listOf("-noverify", "-XX:TieredStopAtLevel=1", "-Dcom.sun.management.jmxremote")
            classpath = classpath.plus(configurations["developmentOnly"])
        }
    }

    named<ShadowJar>("shadowJar") {
        mergeServiceFiles()
    }
}
