package io.micronaut.data.jdbc.h2;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.TableRatings;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface H2TableRatingsRepository extends CrudRepository<TableRatings, Long> {

    @Nullable
    TableRatings findByRating(int rating);

    void updateRating(@Id Long id, int rating);
}
