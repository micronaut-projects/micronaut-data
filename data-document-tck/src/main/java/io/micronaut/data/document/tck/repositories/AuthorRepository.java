/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.document.tck.entities.Author;
import io.micronaut.data.repository.CrudRepository;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.stream.Stream;

public interface AuthorRepository extends CrudRepository<Author, String> {

    Author findByName(String name);

    void updateNickname(@Id String id, @Parameter("nickName") @Nullable String nickName);

    @NonNull
    @Override
    @Join(value = "books")
    @Join(value = "books.pages")
    Optional<Author> findById(@NonNull @NotNull String id);

    @Join("books")
    Author queryByName(String name);

    Author findByBooksTitle(String title);

    @Join("books")
    Author searchByName(String name);

    Stream<Author> queryByNameRegex(String name);

}
