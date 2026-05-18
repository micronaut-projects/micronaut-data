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
package io.micronaut.data.r2dbc.postgres;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.repositories.AuthorRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresAuthorRepository extends AuthorRepository {

    @Query("""
        INSERT INTO "author" ("name", "nick_name")
        VALUES (:name, :nickName)
        RETURNING *
        """)
    Author customInsertReturningAuthor(String name, @Nullable String nickName);

    @Override
    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    List<Author> listAll();

    @Override
    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    Author queryByName(String name);

}
