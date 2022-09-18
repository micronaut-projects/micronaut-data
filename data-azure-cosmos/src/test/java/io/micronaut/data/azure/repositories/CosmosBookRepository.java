package io.micronaut.data.azure.repositories;

import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.document.tck.repositories.BookRepository;

@CosmosRepository
public abstract class CosmosBookRepository extends BookRepository {

    public CosmosBookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

//    @Join("author.books")
//    public abstract Iterable<BsonDocument> queryAll();
}
