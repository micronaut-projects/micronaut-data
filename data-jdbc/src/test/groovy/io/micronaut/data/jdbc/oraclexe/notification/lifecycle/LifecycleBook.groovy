package io.micronaut.data.jdbc.oraclexe.notification.lifecycle

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity(value = 'notification_lifecycle_book', schema = 'TEST')
class LifecycleBook {
    @Id
    Long id

    String title
}
