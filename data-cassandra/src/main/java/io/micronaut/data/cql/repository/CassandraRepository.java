package io.micronaut.data.cql.repository;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.repository.CrudRepository;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Optional;

public interface CassandraRepository<E,ID> extends CrudRepository<E, ID> {
}
