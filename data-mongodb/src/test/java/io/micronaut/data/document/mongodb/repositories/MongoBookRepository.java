package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.tck.entities.Book;
import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.document.tck.repositories.BookRepository;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateReturningQuery;
import com.mongodb.client.model.ReturnDocument;

import jakarta.transaction.Transactional;
import java.util.Optional;

@MongoRepository
public abstract class MongoBookRepository extends BookRepository {

    public MongoBookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public abstract Optional<Book> queryById(String id);

    @MongoUpdateReturningQuery(filter = "{_id: {$eq: :id}}", update = "{$inc:{totalPages: 1} }")
    public abstract Book incrementTotalPages(String id);

    @MongoUpdateReturningQuery(filter = "{_id: {$eq: :id}}", update = "{$inc:{totalPages: 1} }", returnDocument = ReturnDocument.AFTER)
    public abstract Book incrementTotalPagesAfter(String id);
}
