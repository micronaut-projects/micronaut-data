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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Author;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;

public interface AuthorRepository extends CrudRepository<Author, Long> {

    @NonNull
    @Override
    @Join(value = "books", alias = "b", type = Join.Type.LEFT_FETCH)
    @Join(value = "books.pages", alias = "bp", type = Join.Type.LEFT_FETCH)
    Optional<Author> findById(@NonNull @NotNull Long aLong);

    Author findByName(String name);

    Author findByBooksTitle(String title);

    @Join("books")
    Author findByBooksTitleForUpdate(String title);

    long countByNameContains(String text);

    Author findByNameStartsWith(String name);

    List<Author> findByNameContains(String name);


    Author findByNameEndsWith(String name);

    Author findByNameIgnoreCase(String name);

    @Join("books")
    Author searchByName(String name);

    @Join("books")
    List<Author> listAll();

    void updateNickname(@Id Long id, @Parameter("nickName") @Nullable String nickName);
}
