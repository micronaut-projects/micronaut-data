package io.micronaut.data.jdbc.sqlite.jakarta_data.entity;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * A repository that performs operations on different types of entities.
 */
@JdbcRepository(dialect = Dialect.SQLITE)
public interface MultipleEntityRepo { // Do not add a primary entity type.

    // Methods for Box entity:

    @Insert
    Box[] addAll(Box... boxes);

    @Query("DELETE FROM Box")
    long removeAll();

    @Query("UPDATE Box SET length = length + ?1, width = width - ?1, height = height * ?2")
    long resizeAll(int lengthIncrementWidthDecrement, int heightFactor);

    // Methods for Coordinate entity:

    @Insert
    Coordinate create(Coordinate c);

    @Query("DELETE FROM Coordinate WHERE x > 0.0d AND y > 0.0f")
    long deleteIfPositive();

    @Query("DELETE FROM Coordinate WHERE x > 0.0d AND y > 0.0f")
    void deleteIfPositiveWithoutReturnRecords();

    @Query("UPDATE Coordinate SET x = :newX, y = y / :yDivisor WHERE id = :id")
    boolean move(UUID id, double newX, float yDivisor);

    @Query("WHERE id = ?1")
    Optional<Coordinate> withUUID(UUID id);
}
