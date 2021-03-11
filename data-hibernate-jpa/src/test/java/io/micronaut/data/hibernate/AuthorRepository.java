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
package io.micronaut.data.hibernate;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Author;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import java.util.Optional;

@Repository
@Transactional
public interface AuthorRepository extends io.micronaut.data.tck.repositories.AuthorRepository {

    @NonNull
    @EntityGraph(attributePaths = {"books", "books.pages"})
    Optional<Author> queryById(@NonNull @NotNull Long aLong);

}
