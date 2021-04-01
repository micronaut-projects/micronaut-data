package io.micronaut.data.r2dbc.sqlserver;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.repositories.PersonAsyncRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@R2dbcRepository(dialect = Dialect.SQL_SERVER)
public interface MSPersonAsyncRepository extends PersonAsyncRepository {

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, 1)")
    CompletableFuture<Void> saveCustom(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, 1)")
    CompletableFuture<Void> saveCustomSingle(Person people);

}
