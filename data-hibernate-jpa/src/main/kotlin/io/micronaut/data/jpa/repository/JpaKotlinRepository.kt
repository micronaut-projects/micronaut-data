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
package io.micronaut.data.jpa.repository

import io.micronaut.data.annotation.QueryHint
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.jpa.repository.intercept.FlushInterceptor
import io.micronaut.data.jpa.repository.intercept.LoadInterceptor
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.KotlinPageableRepository
import io.micronaut.data.repository.PageableRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import java.io.Serializable
import javax.validation.Valid

/**
 * Simple composed repository interface that includes [CrudRepository] and [PageableRepository].
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @since 1.0.0
 * @author graemerocher
</ID></E> */
interface JpaKotlinRepository<E, ID> : KotlinCrudRepository<E, ID>, KotlinPageableRepository<E, ID> {

    override fun <S : E> saveAll(entities: @Valid Iterable<S>): Iterable<S>

    override fun findAll(): List<E>

    override fun findAll(sort: Sort): List<E>

    /**
     * Save and perform a flush() of any pending operations.
     * @param entity The entity
     * @param <S> The entity generic type
     * @return The entity, possibly mutated
    </S> */
    @QueryHint(name = "javax.persistence.FlushModeType", value = "AUTO")
    fun <S : E> saveAndFlush(entity: @Valid S): S

    /**
     * Adds a flush method.
     */
    @DataMethod(interceptor = FlushInterceptor::class)
    fun flush()

    /**
     * Adds a load method.
     * @param id the entity ID
     * @param <S> the entity type
     * @return An uninitialized proxy
    </S> */
    @DataMethod(interceptor = LoadInterceptor::class)
    fun <S : E> load(id: Serializable): S
}
