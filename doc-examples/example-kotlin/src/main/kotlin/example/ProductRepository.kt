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
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.jpa.annotation.EntityGraph
import io.micronaut.data.repository.CrudRepository
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletableFuture
// tag::join[]
// tag::async[]
@Repository
interface ProductRepository : CrudRepository<Product, Long> {
// end::join[]
// end::async[]

    // tag::join[]
    fun save(manufacturer: Manufacturer) : Manufacturer

    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    fun list(): List<Product>
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = ["manufacturer", "title"]) // <1>
    override fun findAll(): List<Product>
    // end::entitygraph[]

    // tag::async[]
    @Join("manufacturer")
    fun findByNameContains(str: String): CompletableFuture<Product>

    fun countByManufacturerName(name: String): CompletableFuture<Long>
    // end::async[]

    // tag::reactive[]
    @Join("manufacturer")
    fun queryByNameContains(str: String): Maybe<Product>

    fun countDistinctByManufacturerName(name: String): Single<Long>
    // end::reactive[]
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
