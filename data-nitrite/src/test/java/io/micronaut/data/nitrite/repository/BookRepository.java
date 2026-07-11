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
package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Book;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

/**
 * Simple repository backing the Book examples.
 */
@NitriteRepository
public interface BookRepository extends CrudRepository<Book, String> {

  /**
   * Finds a book by exact title.
   *
   * @param title title value
   * @return optional book
   */
  Optional<Book> findByTitle(String title);

  /**
   * Updates the book title identified by {@code id}.
   *
   * @param id document id
   * @param title new title
   */
  void update(@Id String id, String title);
}
