package io.micronaut.data.runtime.intercept

import io.micronaut.core.convert.ConversionService
import spock.lang.Specification
import spock.lang.Unroll

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class PredatorInitializerSpec extends Specification {

    static def DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")

    @Unroll
    def "test date conversion #obj to #targetType"() {
        given:
            new PredatorInitializer()
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
    }

}