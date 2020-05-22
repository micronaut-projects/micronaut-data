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
package io.micronaut.data.hibernate.async;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonDto;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.async.AsyncCrudRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public interface AsyncPersonRepo extends AsyncCrudRepository<Person, Long> {

    CompletableFuture<Person> findByName(String name);

    CompletableFuture<List<Person>> findAllByNameContains(String str);

    CompletableFuture<Person> save(String name, int age);

    CompletableFuture<Page<Person>> findAllByAgeBetween(int start, int end, Pageable pageable);

    CompletableFuture<Integer> updateByName(String name, int age);

    CompletableFuture<PersonDto> searchByName(String name);
}
