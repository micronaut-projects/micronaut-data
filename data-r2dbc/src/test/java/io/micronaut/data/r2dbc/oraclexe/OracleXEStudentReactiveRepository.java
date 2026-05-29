package io.micronaut.data.r2dbc.oraclexe;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Student;
import io.micronaut.data.tck.repositories.StudentReactiveRepository;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.reactivex.rxjava3.core.Single;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEStudentReactiveRepository extends StudentReactiveRepository {

    @Override
    @OracleTransactional(priority = OracleTransactional.Priority.MEDIUM)
    <S extends Student> Single<S> save(S entity);
}
