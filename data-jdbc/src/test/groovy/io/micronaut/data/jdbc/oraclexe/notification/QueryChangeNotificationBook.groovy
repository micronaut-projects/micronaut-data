package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity("query_change_notification_book")
class QueryChangeNotificationBook {
    @Id
    @GeneratedValue
    Long id

    String title
}
