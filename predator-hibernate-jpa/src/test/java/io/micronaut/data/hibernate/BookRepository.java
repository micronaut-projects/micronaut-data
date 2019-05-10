package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
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
