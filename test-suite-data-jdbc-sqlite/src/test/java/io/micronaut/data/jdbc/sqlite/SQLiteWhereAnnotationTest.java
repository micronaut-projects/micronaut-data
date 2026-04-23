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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@MicronautTest
@JavaSQLiteDBProperties
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteWhereAnnotationTest {

    @Inject
    SQLiteEnabledPersonRepository personRepository;

    @BeforeAll
    void setupSpec() {
        personRepository.deleteAll();
    }

    @Test
    void testReturnOnlyEnabledPeople() {
        personRepository.saveAll(java.util.List.of(
            person("Fred", 35, true),
            person("Joe", 30, false),
            person("Bob", 30, true)
        ));

        assertEquals(2, personRepository.count());
        assertEquals(1, personRepository.countByNameLike("%e%"));
        assertFalse(personRepository.findAll().stream().anyMatch(person -> "Joe".equals(person.getName())));
    }

    private static Person person(String name, int age, boolean enabled) {
        Person person = new Person();
        person.setName(name);
        person.setAge(age);
        person.setEnabled(enabled);
        return person;
    }
}
