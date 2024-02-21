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
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.async.AsyncPageableRepository;
import io.micronaut.data.repository.jpa.async.AsyncJpaSpecificationExecutor;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonDto;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface PersonAsyncRepository extends AsyncPageableRepository<Person, Long>, AsyncJpaSpecificationExecutor<Person> {

    CompletionStage<Person> save(String name, int age);

    CompletableFuture<Person> getById(Long id);

    CompletableFuture<Void> updatePerson(@Id Long id, @Parameter("name") String name);

    CompletableFuture<List<Person>> list(Pageable pageable);

    CompletableFuture<Long> count(String name);

    CompletableFuture<Person> findByName(String name);

    CompletableFuture<PersonDto> getByName(String name);

    CompletableFuture<List<PersonDto>> queryByName(String name);

    CompletableFuture<Long> deleteByNameLike(String name);

    CompletableFuture<List<Person>> findByNameLike(String name);

    CompletableFuture<Page<Person>> findByNameLike(String name, Pageable pageable);

    @Query(value = "select * from person person_ where person_.name like :n",
            countQuery = "select count(*) from person person_ where person_.name like :n")
    CompletableFuture<Page<Person>> findPeople(String n, Pageable pageable);

    @Query("SELECT MAX(id) FROM person WHERE id = -1")
    CompletableFuture<Long> getMaxId();

    CompletableFuture<List<Person>> updatePeople(List<Person> people);

    @Query("UPDATE person SET name = :newName WHERE (name = :oldName)")
    CompletableFuture<Long> updateNamesCustom(String newName, String oldName);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    CompletableFuture<Long> saveCustom(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    CompletableFuture<Long> saveCustomSingle(Person people);

    CompletableFuture<Integer> remove(Long id);

    CompletableFuture<Integer> erase(Person people);

    CompletableFuture<Integer> erase(List<Person> people);

    @Query("DELETE FROM person WHERE name = :name")
    CompletableFuture<Integer> deleteCustom(List<Person> people);

    @Query("DELETE FROM person WHERE name = :name")
    CompletableFuture<Integer> deleteCustomSingle(Person person);

    @Query("DELETE FROM person WHERE name = :xyz")
    CompletableFuture<Integer> deleteCustomSingleNoEntity(String xyz);

    @Procedure
    CompletableFuture<Integer> add1(int input);

    @Procedure("add1")
    CompletableFuture<Integer> add1Aliased(int input);
}
