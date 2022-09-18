package io.micronaut.data.azure.repositories;

import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.entities.Author;
import io.micronaut.data.document.tck.repositories.AuthorRepository;

import java.util.stream.Stream;

@CosmosRepository
public interface CosmosAuthorRepository extends AuthorRepository {

    @Override
    default Stream<Author> queryByNameRegex(String name) {
        throw new RuntimeException();
    }
}
