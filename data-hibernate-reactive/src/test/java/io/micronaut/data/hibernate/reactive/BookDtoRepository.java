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
package io.micronaut.data.hibernate.reactive;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BookDtoRepository extends ReactorCrudRepository<Book, Long> {

    @Query("select * from book b where b.title = :title")
    Mono<BookDto> findByTitleWithQuery(String title);

    Flux<BookDto> findByTitleLike(String title);

    Mono<BookDto> findOneByTitle(String title);

    Mono<Page<BookDto>> searchByTitleLike(String title, Pageable pageable);

    Mono<Page<BookDto>> queryAll(Pageable pageable);

}
