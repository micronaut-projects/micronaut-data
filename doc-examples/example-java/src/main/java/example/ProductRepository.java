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
package example;

import io.micronaut.data.annotation.*;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.CrudRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// tag::join[]
// tag::async[]
@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {
// end::join[]
// end::async[]
    // tag::join[]
    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    List<Product> list();
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = {"manufacturer", "title"}) // <1>
    List<Product> findAll();
    // end::entitygraph[]

    // tag::async[]
    @Join("manufacturer")
    CompletableFuture<Product> findByNameContains(String str);

    CompletableFuture<Long> countByManufacturerName(String name);
    // end::async[]
    // tag::reactive[]
    @Join("manufacturer")
    Maybe<Product> queryByNameContains(String str);

    Single<Long> countDistinctByManufacturerName(String name);
    // end::reactive[]
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
