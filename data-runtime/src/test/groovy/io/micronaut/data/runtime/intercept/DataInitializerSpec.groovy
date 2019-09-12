package io.micronaut.data.runtime.intercept

import io.micronaut.core.convert.ConversionService
import spock.lang.Specification
import spock.lang.Unroll

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DataInitializerSpec extends Specification {

    static def DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")

    @Unroll
    def "test date conversion #obj to #targetType"() {
        given:
            new DataInitializer()
            ConversionService<?> conversionService = ConversionService.SHARED

        when:
            def expectedValue = conversionService.convert(obj, targetType)
        then:
            result == expectedValue.get()
        where:
            obj                                          || targetType     || result
            DATE_FORMAT.parse("1970-01-02")              || LocalDate      || LocalDate.parse("1970-01-02")
            DATE_FORMAT.parse("1970-01-02")              || LocalDateTime  || LocalDate.parse("1970-01-02").atStartOfDay()
            DATE_FORMAT.parse("1970-01-02")              || OffsetDateTime || LocalDate.parse("1970-01-02").atStartOfDay().atZone(ZoneId.systemDefault()).toOffsetDateTime()
            LocalDate.parse("1970-01-02")                || Date           || DATE_FORMAT.parse("1970-01-02")
            LocalDate.parse("1970-01-02").atStartOfDay() || Date           || DATE_FORMAT.parse("1970-01-02")
            new Date(1500000000000)                      || Instant        || Instant.ofEpochMilli(1500000000000)
            Instant.ofEpochMilli(1500000000000)      || Date           || new Date(1500000000000)
            LocalDate.parse("1970-01-02")
                    .atTime( LocalTime.of(2,0))
                    .atOffset(ZoneOffset.of("+02:00")) || java.sql.Date  || new java.sql.Date(24 *60 * 60 * 1000)
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))     || Date          || new Date(24 *60 * 60 * 1000)
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))     || Long          || 24 *60 * 60 * 1000
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))     || Timestamp     || new Timestamp(24 *60 * 60 * 1000)
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))     || LocalDateTime || LocalDate.parse("1970-01-02").atTime( LocalTime.of(2,0))


    }
}