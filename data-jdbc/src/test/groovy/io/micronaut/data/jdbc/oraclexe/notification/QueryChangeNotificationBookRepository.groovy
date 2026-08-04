package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.OracleCrudRepository

@JdbcRepository(dialect = Dialect.ORACLE)
interface QueryChangeNotificationBookRepository extends OracleCrudRepository<QueryChangeNotificationBook, Long> {
}
