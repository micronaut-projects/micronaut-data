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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Expandable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.AuthorBooksDto;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;
import io.micronaut.data.tck.entities.Genre;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class BookRepository implements PageableRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    @Join(value = "author", alias = "auth")
    public abstract Book queryByTitle(String title);

    @Query(value = "SELECT book_.* FROM book book_ LEFT JOIN author book_author_ ON book_.author_id = book_author_.id", countQuery = "SELECT count(*) FROM book book_ ")
    public abstract Page<Book> listPageableCustomQuery(Pageable pageable);

    @NonNull
    @Override
    @Join("author")
    public abstract Page<Book> findAll(@NonNull Pageable pageable);

    @Join(value = "author", type = Join.Type.LEFT_FETCH)
    public abstract Page<Book> findByTotalPagesGreaterThan(int totalPages, Pageable pageable);

    protected final AuthorRepository authorRepository;

    public BookRepository(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    public abstract List<Book> findAllByTitleStartingWith(String text);

    public abstract List<Book> findByAuthorIsNull();
    public abstract List<Book> findByAuthorIsNotNull();
    public abstract int countByTitleIsEmpty();
    public abstract int countByTitleIsNotEmpty();

    public abstract List<Book> findByAuthorName(String name);

    public abstract List<Book> findTop3OrderByTitle();

    public abstract Stream<Book> findTop3ByAuthorNameOrderByTitle(String name);

    public abstract List<Book> queryTop3ByAuthorNameOrderByTitle(String name);

    public abstract void deleteByTitleIsEmptyOrTitleIsNull();

    @Join("author")
    public abstract Book findByTitle(String title);

    public abstract Author findAuthorById(@Id Long id);

    @Query(value = "select * from book b where b.title like :arg0 limit 5", nativeQuery = true)
    public abstract List<Book> listNativeBooks(String arg0);

    @Query(value = "select * from book b where b.title in (:arg0)", nativeQuery = true)
    public abstract List<Book> listNativeBooksWithTitleInCollection(@Nullable Collection<String> arg0);

    @Query(value = "select * from book b where b.title IN (:arg0)", nativeQuery = true)
    public abstract List<Book> listNativeBooksWithTitleInArray(@Expandable @TypeDef(type = DataType.STRING) @Nullable String[] arg0);

    @Query(value = "select * from book b where b.title = any (:arg0)", nativeQuery = true)
    public abstract List<Book> listNativeBooksWithTitleAnyCollection(@Nullable Collection<String> arg0);

    @Query(value = "select * from book b where b.title = ANY (:arg0)", nativeQuery = true)
    public abstract List<Book> listNativeBooksWithTitleAnyArray(@TypeDef(type = DataType.STRING) @Nullable String[] arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title = ANY (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableListAsStringArray(@Nullable @TypeDef(type = DataType.STRING_ARRAY) List<String> arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title = ANY (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableArrayAsStringArray(@Nullable @TypeDef(type = DataType.STRING_ARRAY) String[] arg0);

    @Query("UPDATE book SET author_id = :author WHERE id = :id")
    public abstract long updateAuthorCustom(@Parameter("id") Long id, @Parameter("author") Author author);

    public abstract long updateAuthor(@Parameter("id") @Id Long id, @Parameter("author") Author author);

    public abstract void updateByIdInList(List<Long> id, String title);

    public void saveAuthorBooks(List<AuthorBooksDto> authorBooksDtos) {
        List<Author> authors = new ArrayList<>();
        for (AuthorBooksDto dto: authorBooksDtos) {
            Author author = newAuthor(dto.getAuthorName());
            authors.add(author);
            for (BookDto book : dto.getBooks()) {
                newBook(author, book.getTitle(), book.getTotalPages());
            }
        }
        authorRepository.saveAll(authors);
    }

    protected Author newAuthor(String name) {
        Author author = new Author();
        author.setName(name);
        return author;
    }

    protected Book newBook(Author author, String title, int pages) {
        Book book = new Book();
        author.getBooks().add(book);
        book.setAuthor(author);
        book.setTitle(title);
        book.setTotalPages(pages);
        return book;
    }

    public abstract int deleteByIdAndAuthorId(Long id, Long authorId);

    public abstract Book save(String title, int totalPages, Author author);

    public abstract int deleteByAuthor(Author author);

    @Join(value = "genre", type = Join.Type.LEFT_FETCH)
    public abstract List<Book> findAllByGenre(Genre genre);

    @Override
    @Join(value = "genre", type = Join.Type.LEFT_FETCH)
    public abstract Optional<Book> findOne(@Nullable PredicateSpecification<Book> spec);

    @Override
    @Join(value = "genre", type = Join.Type.LEFT_FETCH)
    public abstract  List<Book> findAll(@Nullable PredicateSpecification<Book> spec);

    public abstract List<Book> findAllByCriteria(PredicateSpecification<Book> spec);

    public abstract Book findByTitleOrAuthorAndId(String title, Author author, Long id);

    public abstract List<Book> findAllByChaptersTitle(String chapterTitle);

    @Join(value = "chapters", type = Join.Type.LEFT_FETCH)
    public abstract List<Book> findAllByChaptersTitleAndTitle(String chapterTitle, String title);
}
