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
package io.micronaut.data.jdbc.sqlserver;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.repositories.PersonRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.SQL_SERVER)
public interface MSSQLPersonRepository extends PersonRepository {

    Person save(String name, int age);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, 1)")
    void saveCustom(String name, int age);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, 1)")
    void saveCustom(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, 1)")
    void saveCustomSingle(Person people);

}
