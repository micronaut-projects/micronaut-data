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

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;

import java.util.List;
import java.util.stream.Stream;

public interface BookDtoRepository extends GenericRepository<Book, Long> {


    List<BookDto> findByTitleLike(String title);

    BookDto findOneByTitle(String title);

    Page<BookDto> searchByTitleLike(String title, Pageable pageable);

    Page<BookDto> queryAll(Pageable pageable);

    Stream<BookDto> findStream(String title);
}
