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
package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactorPageableRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PersonReactiveRepository extends ReactorPageableRepository<Person, Long>, ReactorJpaSpecificationExecutor<Person> {

    Mono<Person> save(String name, int age);

    Mono<Person> getById(Long id);

    Mono<Long> updatePerson(@Id Long id, @Parameter("name") String name);

    Flux<Person> list(Pageable pageable);

    Mono<Long> count(String name);

    Mono<Person> findByName(String name);

    Mono<PersonDto> getByName(String name);

    Flux<PersonDto> queryByName(String name);

    Mono<Long> deleteByNameLike(String name);

    Flux<Person> findByNameLike(String name);

    Mono<Page<Person>> findByNameLike(String name, Pageable pageable);

    @Query(value = "select * from person person_ where person_.name like :n",
            countQuery = "select count(*) from person person_ where person_.name like :n")
    Mono<Page<Person>> findPeople(String n, Pageable pageable);

    @Query("SELECT MAX(id) FROM person WHERE id = -1")
    Mono<Long> getMaxId();

    Flux<Person> updatePeople(List<Person> people);

    @Query("UPDATE person SET name = :newName WHERE (name = :oldName)")
    Mono<Long> updateNamesCustom(String newName, String oldName);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    Mono<Long> saveCustom(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    Mono<Long> saveCustomSingle(Person people);

    @Query("DELETE FROM person WHERE name = :name")
    Mono<Long> deleteCustom(List<Person> people);

    @Query("DELETE FROM person WHERE name = :name")
    Mono<Long> deleteCustomSingle(Person person);

    @Query("DELETE FROM person WHERE name = :xyz")
    Mono<Long> deleteCustomSingleNoEntity(String xyz);

    Mono<Long> updatePerson(@Id Long id, int age);
}
