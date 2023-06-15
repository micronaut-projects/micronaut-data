package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.entities.Book;
import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.document.tck.repositories.BookRepository;
import io.micronaut.data.mongodb.annotation.MongoRepository;

import jakarta.transaction.Transactional;
import java.util.Optional;

@MongoRepository
public abstract class MongoBookRepository extends BookRepository {

    public MongoBookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public abstract Optional<Book> queryById(String id);
}
