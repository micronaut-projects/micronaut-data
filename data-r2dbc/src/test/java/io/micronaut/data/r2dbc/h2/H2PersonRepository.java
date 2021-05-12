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
package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.TotalDto;

@R2dbcRepository(dialect = Dialect.H2)
public interface H2PersonRepository extends io.micronaut.data.tck.repositories.PersonRepository {

    Person save(String name, int age);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    int saveCustom(String name, int age);

    @Query("select count(*) as total from person")
    TotalDto getTotal();
}
