package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.entities.Book;
import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.document.tck.repositories.BookRepository;

import java.util.Optional;

@CosmosRepository
public abstract class CosmosBookRepository extends BookRepository {

    public CosmosBookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    public abstract Optional<Book> findById(String id, PartitionKey partitionKey);

//    @Join("author.books")
//    public abstract Iterable<BsonDocument> queryAll();
}
