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
package io.micronaut.data.hibernate.reactive

import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.GenericRepository
import io.micronaut.data.tck.entities.Person
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface PersonRepository extends GenericRepository<Person, Long> {

    Flux<Person> listTop10(Sort sort);

    Mono<Page<Person>> findByNameLike(String name, Pageable pageable)

    Mono<Page<Person>> list(Pageable pageable)

    Mono<Person> findByName(String name)

    Flux<Person> findAllByName(String name)

    Flux<Person> findAllByNameLike(String name, Pageable pageable)

}
