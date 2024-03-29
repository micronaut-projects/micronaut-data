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

import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.hibernate.reactive.entities.Pet
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux

@Repository
interface PetRepository extends ReactorCrudRepository<Pet, UUID> {

    Flux<Pet.PetType> listDistinctType()

    @Query(value = "SELECT DISTINCT type FROM pet", nativeQuery = true)
    Flux<Pet.PetType> findPetTypesNative()

    @Query(value = "SELECT DISTINCT name FROM pet", nativeQuery = true)
    Flux<String> findPetNamesNative()

    @Query(value = "SELECT DISTINCT p.type FROM Pet p")
    Flux<Pet.PetType> findPetTypesHQL()
}

