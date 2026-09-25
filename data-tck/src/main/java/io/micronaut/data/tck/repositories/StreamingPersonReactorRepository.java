/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.annotation.Fetch;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonWithIdAndNameDto;
import reactor.core.publisher.Flux;

public interface StreamingPersonReactorRepository extends ReactorCrudRepository<Person, Long> {

    /**
     * Stream all Person entities.
     *
     * @return a Flux stream of Person entities
     */
    Flux<Person> list();

    /**
     * Queries all Person entities with a fetch size of 1000.
     *
     * This method returns a Flux stream of Person entities, allowing for efficient
     * handling of large datasets by fetching results in batches.
     *
     * @return a Flux stream of Person entities
     */
    @Fetch(1000)
    Flux<Person> queryAll();

    /**
     * Stream a DTO projection (id, name) for all persons.
     *
     * @return a Flux stream of {@link PersonWithIdAndNameDto} objects representing the id and name of all persons
     */
    @Query("SELECT id, name FROM person")
    Flux<PersonWithIdAndNameDto> listAllDto();

    /**
     * Queries all Person entities as a DTO projection (id, name) with a fetch size of 1000.
     *
     * This method returns a Flux stream of {@link PersonWithIdAndNameDto} objects,
     * representing the id and name of all persons, allowing for efficient handling
     * of large datasets by fetching results in batches.
     *
     * @return a Flux stream of {@link PersonWithIdAndNameDto} objects
     */
    @Query("SELECT id, name FROM person")
    @Fetch(1000)
    Flux<PersonWithIdAndNameDto> queryAllDto();
}
