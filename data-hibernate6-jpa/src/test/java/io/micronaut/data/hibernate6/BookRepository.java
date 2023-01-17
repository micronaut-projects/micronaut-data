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
package io.micronaut.data.hibernate6;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.hibernate6.jpa.annotation.EntityGraph;
import io.micronaut.data.hibernate6.jpa.repository.JpaSpecificationExecutor;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.AuthorRepository;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;

@Repository
@Transactional
public abstract class BookRepository extends io.micronaut.data.tck.repositories.BookRepository implements JpaSpecificationExecutor<Book> {
    public BookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    /**
     * @deprecated Order by 'author.name' case without a join. Hibernate will do the cross join if the association property is accessed by the property path without join.
     */
    @Query(value = "SELECT book_ FROM Book book_", countQuery = "SELECT count(book_) FROM Book book_ ")
    @Join(value = "author", type = Join.Type.FETCH)
    @Deprecated
    public abstract Page<Book> listPageableCustomQuery(Pageable pageable);

    /**
     * @deprecated Order by 'author.name' case without a join. Hibernate will do the cross join if the association property is accessed by the property path without join.
     */
    @Query(value = "SELECT book_ FROM Book book_", countQuery = "SELECT count(book_) FROM Book book_ ")
    @Deprecated
    public abstract Page<Book> listPageableCustomQuery2(Pageable pageable);

    @EntityGraph(
            attributePaths = {
                    "totalPages",
                    "author"
            }
    )
    abstract List<Book> findAllByTitleStartsWith(String text);

    @Where(value = "totalPages > :pages")
    abstract List<Book> findByTitleStartsWith(String title, int pages);

    @Query(value = "select count(*) from book b where b.title like :title and b.total_pages > :pages", nativeQuery = true)
    abstract int countNativeByTitleWithPagesGreaterThan(String title, int pages);

    @Query(value = "select * from book where (CASE WHEN CAST(:arg0 AS VARCHAR) is not null THEN title = :arg0 ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableSearch(@Nullable String arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableListSearch(@Nullable List<String> arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE :arg1 END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableListSearchWithExtraParameter(@Nullable List<String> arg0, boolean arg1);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableArraySearch(@Nullable String[] arg0);

    public abstract List<Book> updateBooks(Collection<Book> books);

    @Query("UPDATE Book SET title = :newName WHERE (title = :oldName)")
    public abstract long updateNamesCustom(String newName, String oldName);

    @Query("UPDATE Book SET title = :title where id = :id")
    public abstract long updateCustomOnlyTitles(Collection<Book> books);

    @Query("UPDATE Book SET author = :author WHERE id = :id")
    public abstract long updateAuthorCustomQuery(Long id, Author author);

    public abstract long updateAuthor(@Id Long id, Author author);

    @Query("SELECT b FROM Book b WHERE b.author = :author")
    public abstract List<Book> findByAuthor(Author author);

    @Query("INSERT INTO Book(title, pages, author) VALUES (:title, :pages, :author)")
    public abstract void saveCustom(Collection<Book> books);

    @Query("INSERT INTO Book(title, pages, author) VALUES (:title, :pages, :author)")
    public abstract void saveCustomSingle(Book book);

    @Query("DELETE FROM Book WHERE title = :title")
    public abstract int deleteCustom(Collection<Book> books);

    @Query("DELETE FROM Book WHERE title = :title")
    public abstract int deleteCustomSingle(Book book);

    @Query("DELETE FROM Book WHERE title = :name")
    public abstract int deleteCustomByName(String name);

    @Query(nativeQuery = true, value = "select * from book limit 1")
    public abstract Book findFirstBook();
}
