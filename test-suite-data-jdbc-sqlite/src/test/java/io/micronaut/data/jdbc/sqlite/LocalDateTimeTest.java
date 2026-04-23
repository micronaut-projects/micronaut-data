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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.tck.entities.BasicTypes;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties
class LocalDateTimeTest {

    @Inject
    SQLiteBasicTypesRepository repository;

    @Test
    void testLocalDateTimeDst() throws Exception {
        ZoneId zoneId = ZoneId.of("Europe/Berlin");
        ZonedDateTime dstChange = LocalDateTime.of(2020, 3, 29, 2, 0).atZone(zoneId);
        BasicTypes basicTypes = new BasicTypes();
        basicTypes.setZonedDateTime(dstChange);
        repository.save(basicTypes);

        assertEquals(dstChange, repository.findById(1L).orElseThrow().getZonedDateTime().withZoneSameInstant(zoneId));
    }

    @Test
    void testLocalDateTimeUtc() throws Exception {
        ZoneId utc = ZoneId.of("UTC");
        ZonedDateTime dstChange = LocalDateTime.of(2020, 3, 29, 2, 0).atZone(utc);
        BasicTypes basicTypes = new BasicTypes();
        basicTypes.setZonedDateTime(dstChange);
        repository.save(basicTypes);

        assertEquals(dstChange, repository.findById(basicTypes.getMyId()).orElseThrow().getZonedDateTime().withZoneSameInstant(utc));
    }
}
