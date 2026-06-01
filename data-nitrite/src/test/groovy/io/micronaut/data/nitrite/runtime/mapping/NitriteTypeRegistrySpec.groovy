package io.micronaut.data.nitrite.runtime.mapping

import io.micronaut.data.nitrite.runtime.ValueConverter
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.*
import java.time.format.DateTimeFormatter

class NitriteTypeRegistrySpec extends Specification {

    // Fixed values used across write/read round-trips
    static final Instant        INSTANT  = Instant.ofEpochSecond(1609459200L, 123456789)
    static final LocalDate      DATE     = LocalDate.of(2021, 1, 1)
    static final LocalDateTime  DATETIME = LocalDateTime.of(2021, 1, 15, 10, 30, 45, 123456789)
    static final LocalTime      TIME     = LocalTime.of(10, 30, 0, 123456789)
    // Fixed-offset zone so ISO string round-trips without zone-name vs offset mismatch
    static final ZonedDateTime  ZDT      = ZonedDateTime.of(2021, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(-5))
    static final OffsetDateTime ODT      = OffsetDateTime.of(2021, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(-5))
    static final UUID           TEST_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

    // -------------------------------------------------------------------------
    // write: Java value → Nitrite storage format
    // -------------------------------------------------------------------------

    @Unroll
    def "write: #desc"() {
        expect:
        NitriteTypeRegistry.write(value) == expected

        where:
        desc                | value                           | expected
        "null"              | null                            | null
        "Instant"           | INSTANT                         | ValueConverter.epochNanos(INSTANT)
        "LocalDate"         | DATE                            | DATE.toEpochDay()
        "LocalDateTime"     | DATETIME                        | ValueConverter.epochNanos(DATETIME.toInstant(ZoneOffset.UTC))
        "LocalTime"         | TIME                            | TIME.toNanoOfDay()
        "ZonedDateTime"     | ZDT                             | ZDT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        "OffsetDateTime"    | ODT                             | ODT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        "UUID"              | TEST_UUID                       | TEST_UUID.toString()
        "URL"               | new URL("https://example.com") | "https://example.com"
        "URI"               | new URI("urn:isbn:0451450523") | "urn:isbn:0451450523"
        "Charset"           | Charset.forName("UTF-8")        | "UTF-8"
        "ZoneId"            | ZoneId.of("Europe/Paris")       | "Europe/Paris"
    }

    def "write: unregistered type returns value unchanged"() {
        given:
        def value = new Object()

        expect:
        NitriteTypeRegistry.write(value).is(value)
    }

    def "write: Charset concrete subclass resolved via supertype walk"() {
        expect:
        NitriteTypeRegistry.write(StandardCharsets.UTF_8)      == "UTF-8"
        NitriteTypeRegistry.write(StandardCharsets.ISO_8859_1) == "ISO-8859-1"
        NitriteTypeRegistry.write(StandardCharsets.US_ASCII)   == "US-ASCII"
    }

    def "write: ZoneId concrete subclass resolved via supertype walk"() {
        expect:
        NitriteTypeRegistry.write(ZoneOffset.UTC)         == "Z"
        NitriteTypeRegistry.write(ZoneOffset.ofHours(5))  == "+05:00"
        NitriteTypeRegistry.write(ZoneOffset.ofHours(-8)) == "-08:00"
    }

    // -------------------------------------------------------------------------
    // read: stored Number → Java temporal
    // -------------------------------------------------------------------------

    @Unroll
    def "read Number: #desc"() {
        expect:
        NitriteTypeRegistry.read(stored, type) == expected

        where:
        desc            | type          | stored                                                        | expected
        "Instant"       | Instant       | ValueConverter.epochNanos(INSTANT)                            | INSTANT
        "LocalDate"     | LocalDate     | DATE.toEpochDay()                                             | DATE
        "LocalDateTime" | LocalDateTime | ValueConverter.epochNanos(DATETIME.toInstant(ZoneOffset.UTC)) | DATETIME
        "LocalTime"     | LocalTime     | TIME.toNanoOfDay()                                            | TIME
    }

    // -------------------------------------------------------------------------
    // read: stored String → Java temporal
    // -------------------------------------------------------------------------

    @Unroll
    def "read String: #desc"() {
        expect:
        NitriteTypeRegistry.read(stored, type) == expected

        where:
        desc            | type           | stored                                                | expected
        "LocalDate"     | LocalDate      | DATE.toString()                                       | DATE
        "LocalDateTime" | LocalDateTime  | DATETIME.toString()                                   | DATETIME
        "LocalTime"     | LocalTime      | TIME.toString()                                       | TIME
        "ZonedDateTime" | ZonedDateTime  | ZDT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)    | ZDT
        "OffsetDateTime"| OffsetDateTime | ODT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)    | ODT
    }

    // -------------------------------------------------------------------------
    // read: no-op cases (no reverse function registered)
    // -------------------------------------------------------------------------

    def "read: returns null when no reverse function applies"() {
        expect:
        NitriteTypeRegistry.read(1609459200L, ZonedDateTime) == null  // no fromNumber for ZonedDateTime
        NitriteTypeRegistry.read(1609459200L, OffsetDateTime) == null // no fromNumber for OffsetDateTime
        NitriteTypeRegistry.read("text", UUID) == null                 // no fromString for UUID
        NitriteTypeRegistry.read("text", Object) == null               // unregistered type
        NitriteTypeRegistry.read(null, Instant) == null                // null input
    }

    // -------------------------------------------------------------------------
    // strategyFor / hasEntry
    // -------------------------------------------------------------------------

    @Unroll
    def "strategyFor: #desc → #strategy"() {
        expect:
        NitriteTypeRegistry.strategyFor(type) == strategy

        where:
        desc            | type           | strategy
        "Instant"       | Instant        | PropertyStrategy.INSTANT
        "LocalDate"     | LocalDate      | PropertyStrategy.LOCAL_DATE
        "LocalDateTime" | LocalDateTime  | PropertyStrategy.LOCAL_DATETIME
        "LocalTime"     | LocalTime      | PropertyStrategy.LOCAL_TIME
        "ZonedDateTime" | ZonedDateTime  | PropertyStrategy.ZONED_DATE_TIME
        "OffsetDateTime"| OffsetDateTime | PropertyStrategy.OFFSET_DATE_TIME
        "UUID"          | UUID           | PropertyStrategy.UUID
        "URL"           | URL            | PropertyStrategy.URL
        "URI"           | URI            | PropertyStrategy.URI
        "Charset"       | Charset        | PropertyStrategy.CHARSET
        "ZoneId"        | ZoneId         | PropertyStrategy.JAVA_PASSTHROUGH
    }

    def "hasEntry: registered types and their subtypes"() {
        expect:
        NitriteTypeRegistry.hasEntry(Instant)
        NitriteTypeRegistry.hasEntry(LocalDate)
        NitriteTypeRegistry.hasEntry(LocalDateTime)
        NitriteTypeRegistry.hasEntry(LocalTime)
        NitriteTypeRegistry.hasEntry(ZonedDateTime)
        NitriteTypeRegistry.hasEntry(OffsetDateTime)
        NitriteTypeRegistry.hasEntry(UUID)
        NitriteTypeRegistry.hasEntry(Charset)
        NitriteTypeRegistry.hasEntry(ZoneId)
        NitriteTypeRegistry.hasEntry(StandardCharsets.UTF_8.class)  // concrete subclass
        NitriteTypeRegistry.hasEntry(ZoneOffset)                    // concrete subclass of ZoneId
        !NitriteTypeRegistry.hasEntry(Object)
        !NitriteTypeRegistry.hasEntry(String)
        !NitriteTypeRegistry.hasEntry(Integer)
        !NitriteTypeRegistry.hasEntry(Optional)
    }
}
