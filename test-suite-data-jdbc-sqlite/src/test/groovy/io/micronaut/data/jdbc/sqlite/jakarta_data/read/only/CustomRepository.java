package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Insert;

import java.util.List;
import java.util.Set;

/**
 * Do not add methods or inheritance to this interface.
 * Its purpose is to test that without inheriting from a built-in repository,
 * the lifecycle methods with the same entity class are what identifies the
 * primary entity class to use for the count and exist methods.
 */
@JdbcRepository(dialect = Dialect.ANSI)
public interface CustomRepository {

    @Insert
    void add(List<NaturalNumber> list);

    long countByIdIn(Set<Long> ids);

    boolean existsByIdIn(Set<Long> ids);

    @Delete
    void remove(List<NaturalNumber> list);
}
