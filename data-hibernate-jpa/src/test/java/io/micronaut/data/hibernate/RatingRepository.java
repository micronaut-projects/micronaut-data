package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.Rating;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.CrudRepository;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface RatingRepository extends CrudRepository<Rating, UUID> {
    @NotNull
    @EntityGraph("RatingEntityGraph")
    Optional<Rating> findById(@NotNull UUID id);

    @EntityGraph(attributePaths = { "book.pages", "book.author" })
    Optional<Rating> queryById(@NotNull UUID id);

    @EntityGraph(attributePaths = { "book.author", "book.pages", "author.books" })
    Optional<Rating> getById(@NotNull UUID id);
}
