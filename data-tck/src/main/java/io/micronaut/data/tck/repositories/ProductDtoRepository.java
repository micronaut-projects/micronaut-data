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

import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Product;
import io.micronaut.data.tck.entities.ProductDto;

import java.util.List;
import java.util.Optional;

public interface ProductDtoRepository extends GenericRepository<Product, Long> {

    @Query("select p.id, p.name, p.price, p.loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name AS long_name, " +
        "p.date_created, p.last_updated from product p where p.name = :name order by p.name")
    Optional<ProductDto> findByNameWithQuery(String name);

    List<ProductDto> findByNameLikeOrderByName(String name);
}
