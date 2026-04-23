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

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties
class SQLiteOrderTest {

    @Inject
    SQLitePersonRepository personRepository;

    @Test
    void testOrderCaseInsensitive() {
        personRepository.save(person("ABC4"));
        personRepository.save(person("abc3"));
        personRepository.save(person("abc2"));
        personRepository.save(person("ABC1"));

        Sort.Order order = new Sort.Order("name", Sort.Order.Direction.ASC, true);
        var list = personRepository.list(Pageable.from(0, 10).order(order));

        assertEquals("ABC1", list.get(0).getName());
        assertEquals("abc2", list.get(1).getName());
        assertEquals("abc3", list.get(2).getName());
        assertEquals("ABC4", list.get(3).getName());
    }

    private static Person person(String name) {
        Person person = new Person();
        person.setName(name);
        return person;
    }
}
