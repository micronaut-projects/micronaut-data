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
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// tag::join[]
// tag::async[]
@JdbcRepository(dialect = Dialect.H2)
public interface ProductRepository extends CrudRepository<Product, Long> {
// end::join[]
// end::async[]
    // tag::join[]
    Manufacturer saveManufacturer(String name);

    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    List<Product> list();
    // end::join[]

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

    // tag::native[]
    @Query("""SELECT *, m_.name as m_name, m_.id as m_id 
              FROM product p 
              INNER JOIN manufacturer m_ ON p.manufacturer_id = m_.id 
              WHERE p.name like :name limit 5""")
    @Join(value = "manufacturer", alias = "m_")
    List<Product> searchProducts(String name);
    // end::native[]
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
