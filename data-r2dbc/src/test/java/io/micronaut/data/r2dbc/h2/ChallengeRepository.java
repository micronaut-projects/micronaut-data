package io.micronaut.data.r2dbc.h2;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Challenge;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;

@R2dbcRepository(dialect = Dialect.H2)
public interface ChallengeRepository extends CrudRepository<Challenge, Long> {

    @Override
    @Join(value = "authentication")
    @Join(value = "authentication.device")
    @Join(value = "authentication.device.user")
    @NonNull
    Optional<Challenge> findById(@NonNull @NotNull Long id);
}
