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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Expandable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.hibernate.reactive.repository.jpa.ReactorJpaSpecificationExecutor;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.reactive.ReactorPageableRepository;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.AuthorBooksDto;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookRepository extends ReactorPageableRepository<Book, Long>, ReactorJpaSpecificationExecutor<Book> {

    default Mono<Void> saveAuthorBooks(AuthorRepository authorRepository, List<AuthorBooksDto> authorBooksDtos) {
        List<Author> authors = new ArrayList<>();
        for (AuthorBooksDto dto : authorBooksDtos) {
            Author author = newAuthor(dto.getAuthorName());
            authors.add(author);
            for (BookDto book : dto.getBooks()) {
                newBook(author, book.getTitle(), book.getTotalPages());
            }
        }
        return authorRepository.saveAll(authors).then();
    }

    default Author newAuthor(String name) {
        Author author = new Author();
        author.setName(name);
        return author;
    }

    default Book newBook(Author author, String title, int pages) {
        Book book = new Book();
        author.getBooks().add(book);
        book.setAuthor(author);
        book.setTitle(title);
        book.setTotalPages(pages);
        return book;
    }

    @EntityGraph(
            attributePaths = {
                    "totalPages",
                    "author"
            }
    )
    Flux<Book> findAllByTitleStartsWith(String text);

    Flux<Book> findAllByTitleStartingWith(String text);

    @Where(value = "total_pages > :pages")
    Flux<Book> findByTitleStartsWith(String title, int pages);

    @Join("author")
    @Override
    Mono<Page<Book>> findAll(Pageable pageable);

    Flux<Book> findTop3OrderByTitle();

    Flux<Book> findTop3ByAuthorNameOrderByTitle(String name);

    Flux<Book> findByAuthorName(String name);

    @Join("author")
    Mono<Book> findByTitle(String name);

    @Query(value = "select count(*) from book b where b.title like :title and b.total_pages > :pages", nativeQuery = true)
    Mono<Integer> countNativeByTitleWithPagesGreaterThan(String title, int pages);

    @Query(value = "select * from book where (CASE WHEN :arg0 is not null THEN title = :arg0 ELSE true END)", nativeQuery = true)
    Flux<Book> listNativeBooksNullableSearch(@Nullable String arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    Flux<Book> listNativeBooksNullableListSearch(@Nullable List<String> arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE :arg1 END)", nativeQuery = true)
    Flux<Book> listNativeBooksNullableListSearchWithExtraParameter(@Nullable List<String> arg0, boolean arg1);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    Flux<Book> listNativeBooksNullableArraySearch(@Nullable String[] arg0);

    @Query(value = "select * from book b where b.title in (:arg0)", nativeQuery = true)
    Flux<Book> listNativeBooksWithTitleInCollection(@Nullable Collection<String> arg0);

    @Query(value = "select * from book b where b.title IN (:arg0)", nativeQuery = true)
    Flux<Book> listNativeBooksWithTitleInArray(@Expandable @TypeDef(type = DataType.STRING) @Nullable String[] arg0);

    @Query(value = "select * from book b where b.title = any (:arg0)", nativeQuery = true)
    Flux<Book> listNativeBooksWithTitleAnyCollection(@Nullable Collection<String> arg0);

    @Query(value = "select * from book b where b.title = ANY (:arg0)", nativeQuery = true)
    Flux<Book> listNativeBooksWithTitleAnyArray(@TypeDef(type = DataType.STRING) @Nullable String[] arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title = ANY (:arg0) ELSE true END)", nativeQuery = true)
    Flux<Book> listNativeBooksNullableListAsStringArray(@Nullable @TypeDef(type = DataType.STRING_ARRAY) List<String> arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title = ANY (:arg0) ELSE true END)", nativeQuery = true)
    Flux<Book> listNativeBooksNullableArrayAsStringArray(@Nullable @TypeDef(type = DataType.STRING_ARRAY) String[] arg0);

    Flux<Book> updateBooks(Collection<Book> books);

    @Query("UPDATE Book SET title = :newName WHERE (title = :oldName)")
    Mono<Long> updateNamesCustom(String newName, String oldName);

    @Query("UPDATE Book SET title = :title where id = :id")
    Mono<Long> updateCustomOnlyTitles(Collection<Book> books);

    @Query("UPDATE Book SET author = :author WHERE id = :id")
    Mono<Long> updateAuthorCustomQuery(Long id, Author author);

    Mono<Long> updateAuthor(@Id Long id, Author author);

    @Query("SELECT b FROM Book b WHERE b.author = :author")
    Flux<Book> findByAuthor(Author author);

    @Query("INSERT INTO Book(title, pages, author) VALUES (:title, :pages, :author)")
    Mono<Void> saveCustom(Collection<Book> books);

    @Query("INSERT INTO Book(title, pages, author) VALUES (:title, :pages, :author)")
    Mono<Void> saveCustomSingle(Book book);

    @Query("DELETE FROM Book WHERE title = :title")
    Mono<Integer> deleteCustom(Collection<Book> books);

    @Query("DELETE FROM Book WHERE title = :title")
    Mono<Integer> deleteCustomSingle(Book book);

    @Query("DELETE FROM Book WHERE title = :name")
    Mono<Integer> deleteCustomByName(String name);

    @Query(value = "select * from book b where b.title like :arg0 limit 5", nativeQuery = true)
    Flux<Book> listNativeBooks(String arg0);

    @Query(value = "select * from book limit 1", nativeQuery = true)
    Mono<Book> findFirstBook();
}
