package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.test.annotation.MicronautTest
import spock.lang.PendingFeature
import spock.lang.Specification

import javax.inject.Inject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime


@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class LocalDateTimeSpec extends Specification {

    @Inject
    H2BasicTypeRepository repository;


    void "test local date time - DST"() {

        given:
        def zoneId = ZoneId.of("Europe/Berlin")
        ZonedDateTime dstChange = LocalDateTime.of(2020, 3, 29, 2, 0)
                                            .atZone(zoneId)
        BasicTypes basicTypes = new BasicTypes()
        basicTypes.setZonedDateTime(dstChange)
        repository.save(basicTypes)

        expect:
        repository.findById(1L).get().getZonedDateTime().withZoneSameInstant(zoneId) == dstChange
    }

    void "test local date time - UTC"() {

        given:
        def utc = ZoneId.of("UTC")
        ZonedDateTime dstChange = LocalDateTime.of(2020, 3, 29, 2, 0)
            .atZone(utc);
        BasicTypes basicTypes = new BasicTypes()
        basicTypes.setZonedDateTime(dstChange)
        repository.save(basicTypes)

        expect:
        repository.findById(basicTypes.getMyId()).get().getZonedDateTime().withZoneSameInstant(utc) == dstChange
    }
}
