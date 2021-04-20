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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Category;

import java.util.List;

public interface CategoryRepository extends CrudRepository<Category, Long> {
    @Join(value = "productList", type = Join.Type.LEFT_FETCH)
    List<Category> findAll();

    @Join(value = "productList", type = Join.Type.LEFT_FETCH)
    List<Category> findAllOrderById();

    @Join(value = "productList", type = Join.Type.LEFT_FETCH)
    List<Category> findAllOrderByName();

    @Join(value = "productList", type = Join.Type.LEFT_FETCH)
    List<Category> findAllOrderByPositionAndName();

    @Join(value = "productList", type = Join.Type.LEFT_FETCH)
    Page<Category> findAll(@NonNull Pageable pageable);
}
