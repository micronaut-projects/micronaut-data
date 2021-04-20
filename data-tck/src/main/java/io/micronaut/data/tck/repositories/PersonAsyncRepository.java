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
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.async.AsyncCrudRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonDto;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface PersonAsyncRepository extends AsyncCrudRepository<Person, Long> {

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

    @Query("SELECT MAX(id) FROM person WHERE id = -1")
    CompletableFuture<Long> getMaxId();

    CompletableFuture<List<Person>> updatePeople(List<Person> people);

    @Query("UPDATE person SET name = :newName WHERE (name = :oldName)")
    CompletableFuture<Long> updateNamesCustom(String newName, String oldName);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    CompletableFuture<Void> saveCustom(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    CompletableFuture<Void> saveCustomSingle(Person people);

    CompletableFuture<Integer> remove(Long id);

    CompletableFuture<Integer> deleteOneReturnRowsDeleted(Person people);

    CompletableFuture<Integer> deleteManyReturnRowsDeleted(List<Person> people);
}
