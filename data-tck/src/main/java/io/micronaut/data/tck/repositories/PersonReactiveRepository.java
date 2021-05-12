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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.reactive.RxJavaCrudRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonDto;
import io.reactivex.*;

import java.util.List;

public interface PersonReactiveRepository extends RxJavaCrudRepository<Person, Long> {

    Single<Person> save(String name, int age);

    Single<Person> getById(Long id);

    Single<Long> updatePerson(@Id Long id, @Parameter("name") String name);

    Flowable<Person> list(Pageable pageable);

    Single<Long> count(String name);

    @Nullable
    Maybe<Person> findByName(String name);

    Single<PersonDto> getByName(String name);

    Flowable<PersonDto> queryByName(String name);

    Single<Long> deleteByNameLike(String name);

    Observable<Person> findByNameLike(String name);

    @Query("SELECT MAX(id) FROM person WHERE id = -1")
    Maybe<Long> getMaxId();

    Flowable<Person> updatePeople(List<Person> people);

    @Query("UPDATE person SET name = :newName WHERE (name = :oldName)")
    Maybe<Long> updateNamesCustom(String newName, String oldName);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    Single<Long> saveCustom(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    Single<Long> saveCustomSingle(Person people);

    @Query("DELETE FROM person WHERE name = :name")
    Single<Long> deleteCustom(List<Person> people);

    @Query("DELETE FROM person WHERE name = :name")
    Single<Long> deleteCustomSingle(Person person);

    @Query("DELETE FROM person WHERE name = :xyz")
    Single<Long> deleteCustomSingleNoEntity(String xyz);

}
