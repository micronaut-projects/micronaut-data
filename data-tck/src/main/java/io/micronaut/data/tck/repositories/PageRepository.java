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
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Chapter;
import io.micronaut.data.tck.entities.Page;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends GenericRepository<Page, Long> {
    Page save(long num);

    @Join(value = "book.chapters", type = Join.Type.LEFT_FETCH)
    Optional<Book> findBookById(Long id);

    // No explicit joins
    List<Chapter> findBookChaptersById(Long id);

    // Join on book so chapters.book will be populated
    @Join(value = "book.chapters.book", type = Join.Type.FETCH)
    List<Chapter> findBookChaptersByIdAndNum(Long id, long num);
}
