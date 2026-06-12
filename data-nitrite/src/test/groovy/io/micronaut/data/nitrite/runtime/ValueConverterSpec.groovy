package io.micronaut.data.nitrite.runtime

import io.micronaut.core.convert.ConversionService
import spock.lang.Specification
import java.time.Instant

class ValueConverterSpec extends Specification {

    void "test epoch nanos"() {
        given:
        Instant now = Instant.now()
        
        when:
        long nanos = ValueConverter.epochNanos(now)
        Instant restored = ValueConverter.fromEpochNanos(nanos)
        
        then:
        restored == now
    }

    void "test toFilterValueStatic"() {
        expect:
        ValueConverter.toFilterValueStatic(null) == null
        ValueConverter.toFilterValueStatic("foo") == "foo"
        ValueConverter.toFilterValueStatic(123) == 123
        ValueConverter.toFilterValueStatic(true) == true
        ValueConverter.toFilterValueStatic('A' as char) == 'A' as char
        ValueConverter.toFilterValueStatic(Thread.State.NEW) == "NEW"
        ValueConverter.toFilterValueStatic(Optional.of("bar")) == "bar"
        ValueConverter.toFilterValueStatic(Optional.empty()) == null
    }

    void "test convert"() {
        given:
        ValueConverter converter = new ValueConverter(ConversionService.SHARED)
        
        expect:
        converter.convert(null, String) == null
        converter.convert("foo", String) == "foo"
        converter.convert("123", Integer) == 123
        converter.convert("not a number", Integer) == "not a number"
    }
}
