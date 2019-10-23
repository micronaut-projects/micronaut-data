package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.repositories.BookRepository;

import java.util.Arrays;

@JdbcRepository(dialect = Dialect.ORACLE)
public abstract class OracleXEBookRepository extends BookRepository {
    public OracleXEBookRepository(OracleXEAuthorRepository authorRepository) {
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
