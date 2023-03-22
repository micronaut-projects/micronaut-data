package io.micronaut.data.hibernate.reactive;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.reactive.entities.Rating;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Repository
public interface RatingRepository extends ReactorCrudRepository<Rating, UUID> {

    @EntityGraph("RatingEntityGraph")
    Mono<Rating> findById(@NotNull UUID id);

    @EntityGraph(attributePaths = { "book.pages", "book.author" })
    Mono<Rating> queryById(@NotNull UUID id);

    @EntityGraph(attributePaths = { "book.author", "book.pages", "author.books" })
    Mono<Rating> getById(@NotNull UUID id);
}
