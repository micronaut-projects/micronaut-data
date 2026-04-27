package io.micronaut.data.r2dbc.oraclexe;

import io.micronaut.data.connection.annotation.TransactionPriority;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Student;
import io.micronaut.data.tck.repositories.StudentReactiveRepository;
import io.micronaut.transaction.annotation.Transactional;
import io.reactivex.Single;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEStudentReactiveRepository extends StudentReactiveRepository {

    @Override
    @Transactional
    @TransactionPriority(TransactionPriority.Level.MEDIUM)
    <S extends Student> Single<S> save(S entity);
}
