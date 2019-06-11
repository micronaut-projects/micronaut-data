/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Repository
@Transactional
public abstract class BookRepository implements CrudRepository<Book, Long> {

    private final AuthorRepository authorRepository;

    public BookRepository(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Query(value = "select * from book b where b.title like :t limit 5", nativeQuery = true)
    abstract List<Book> listNativeBooks(String t);

    @EntityGraph(
        attributePaths = {
                "pages",
                "author"
        }
    )
    abstract List<Book> findAllByTitleStartsWith(String text);

    abstract List<Book> findAllByTitleStartingWith(String text);

    abstract List<Book> findByAuthorIsNull();
    abstract List<Book> findByAuthorIsNotNull();
    abstract int countByTitleIsEmpty();
    abstract int countByTitleIsNotEmpty();

    abstract List<Book> findByAuthorName(String name);

    abstract List<Book> findTop3OrderByTitle();

    abstract Stream<Book> findTop3ByAuthorNameOrderByTitle(String name);

    public void setupData() {
        Author king = newAuthor("Stephen King");
        Author jp = newAuthor("James Patterson");
        Author dw = newAuthor("Don Winslow");

        newBook(king, "The Stand", 100);
        newBook(king, "Pet Cemetery", 400);
        newBook(jp, "Along Came a Spider", 300);
        newBook(jp, "Double Cross", 300);
        newBook(dw, "The Power of the Dog", 600);
        newBook(dw, "The Border", 700);

        authorRepository.saveAll(Arrays.asList(
                king,
                jp,
                dw
        ));
    }

    private Author newAuthor(String name) {
        Author author = new Author();
        author.setName(name);
        return author;
    }

    private Book newBook(Author author, String title, int pages) {
        Book book = new Book();
        author.getBooks().add(book);
        book.setAuthor(author);
        book.setTitle(title);
        book.setPages(pages);
        return book;
    }
}
