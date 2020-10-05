/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.h2


import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@MicronautTest
@H2DBProperties
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
