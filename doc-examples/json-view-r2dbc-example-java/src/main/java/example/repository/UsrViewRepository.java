package example.repository;

import example.domain.view.UsrView;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.PageableRepository;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface UsrViewRepository extends PageableRepository<UsrView, Long> {
}
