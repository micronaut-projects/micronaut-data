package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.Book;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public abstract class BookRepository implements CrudRepository<Book, Long> {

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
        book.setPages(pages);
        return book;
    }
}
