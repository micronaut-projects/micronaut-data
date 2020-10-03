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
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.AuthorBooksDto;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public abstract class BookRepository implements PageableRepository<Book, Long> {

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

    public abstract void deleteByTitleIsEmptyOrTitleIsNull();

    @Join("author")
    public abstract Book findByTitle(String title);

    public abstract Author findAuthorById(@Id Long id);

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
}
