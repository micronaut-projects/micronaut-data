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

import io.micronaut.data.tck.entities.Person;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties
class SQLiteStreamingStatementTest {

    @Inject
    SQLitePersonRepository personRepository;

    @Test
    void testStreamingOrder() {
        personRepository.save(person("a"));
        personRepository.save(person("c"));
        personRepository.save(person("b"));
        personRepository.save(person("d"));

        var list = personRepository.findAllAndStream().toList();

        assertEquals(4, list.size());
        assertEquals("a", list.get(0).get("NAME"));
        assertEquals("b", list.get(1).get("NAME"));
        assertEquals("c", list.get(2).get("NAME"));
        assertEquals("d", list.get(3).get("NAME"));
    }

    private static Person person(String name) {
        Person person = new Person();
        person.setName(name);
        return person;
    }
}
