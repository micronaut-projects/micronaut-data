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

import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Shelf;

import java.util.Optional;

public interface ShelfRepository extends GenericRepository<Shelf, Long> {
    Shelf save(String shelfName);

    Shelf save(Shelf shelf);

    @Join(value = "books", type = Join.Type.LEFT_FETCH, alias = "b_")
    @Join(value = "books.pages", type = Join.Type.LEFT_FETCH, alias = "p_")
    @Join(value = "books.genre", type = Join.Type.LEFT_FETCH, alias = "g_")
    Optional<Shelf> findById(Long id);
}
