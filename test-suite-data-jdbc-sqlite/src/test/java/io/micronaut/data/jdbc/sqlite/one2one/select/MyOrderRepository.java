package io.micronaut.data.jdbc.sqlite.one2one.select;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.repeatable.JoinSpecifications;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

import java.util.UUID;

@JdbcRepository(dialect = Dialect.SQLITE)
public interface MyOrderRepository extends CrudRepository<MyOrder, UUID> {
    @NonNull
    @JoinSpecifications({
        @Join(value = "embedded", type = Join.Type.LEFT_FETCH)
    })
    Page<MyOrder> findAll(@Nullable PredicateSpecification<MyOrder> spec, Pageable pageable);

}
