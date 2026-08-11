package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.ORACLE)
interface ObjectChangeNotificationBookRepository extends CrudRepository<ObjectChangeNotificationBook, Long> {
    @Query(value = "SELECT ROWID FROM object_change_notification_book WHERE id = :id", nativeQuery = true)
    Optional<String> findRowIdById(Long id)

    @Query("UPDATE object_change_notification_book SET title = :title WHERE id IN (:ids)")
    long updateTitleByIds(String title, List<Long> ids)
}
