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
package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Expandable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.connection.annotation.ClientInfo;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;

import io.micronaut.core.annotation.Nullable;
import java.util.Collection;
import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
@ClientInfo.Attribute(name = "OCSID.MODULE", value = "BOOKS")
public abstract class OracleXEBookRepository extends BookRepository {
    public OracleXEBookRepository(OracleXEAuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Query(value = "SELECT book_.* FROM book book_ ORDER BY book_.title ASC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY")
    public abstract List<Book> findBooks(int limit, int offset);

    @Override
    @Query(value = "select * from book b where b.title = any (:arg0)", nativeQuery = true)
    public abstract List<Book> listNativeBooksWithTitleAnyCollection(@Nullable Collection<String> arg0);

    @Override
    @Query(value = "select * from book b where b.title = ANY (:arg0)", nativeQuery = true)
    public abstract List<Book> listNativeBooksWithTitleAnyArray(@Expandable @TypeDef(type = DataType.STRING) @Nullable String[] arg0);

    @Procedure
    public abstract int add1(int input);

    @Procedure("add1")
    public abstract int add1Aliased(int input);

    @Override
    @ClientInfo.Attribute(name = "OCSID.MODULE", value = "CustomModule")
    @ClientInfo.Attribute(name = "OCSID.ACTION", value = "INSERT")
    public abstract @NonNull Book save(@NonNull Book book);

    //    public abstract Book updateReturning(Book book);
//
//    public abstract String updateReturningTitle(Book book);
//
//    public abstract String updateReturningTitle(@Id Long id, String title);
//
//    public abstract String updateByIdReturningTitle(Long id, String title);
}
