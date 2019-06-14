package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.*;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.repositories.AuthorRepository;

import java.util.Arrays;

@JdbcRepository(dialect = Dialect.H2)
public abstract class BookRepository extends io.micronaut.data.tck.repositories.BookRepository {
    public BookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Override
    public void setupData() {
        Author king = newAuthor("Stephen King");
        Author jp = newAuthor("James Patterson");
        Author dw = newAuthor("Don Winslow");


        authorRepository.saveAll(Arrays.asList(
                king,
                jp,
                dw
        ));

        saveAll(Arrays.asList(
            newBook(king, "The Stand", 100),
            newBook(king, "Pet Cemetery", 400),
            newBook(jp, "Along Came a Spider", 300),
            newBook(jp, "Double Cross", 300),
            newBook(dw, "The Power of the Dog", 600),
            newBook(dw, "The Border", 700)
        ));
    }
}
