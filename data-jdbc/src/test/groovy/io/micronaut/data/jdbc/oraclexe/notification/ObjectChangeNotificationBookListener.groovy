package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection

@Singleton
@Requires(property = "query-notification.enabled")
class ObjectChangeNotificationBookListener extends AbstractQueryNotificationBookListener<ObjectChangeNotificationBook> {
    @ChangeListener
    @OracleChangeNotification(properties = [
        @OracleChangeNotification.Property(name = OracleConnection.DCN_CLIENT_INIT_CONNECTION, value = "true")
    ])
    void onBookChanged(ChangeEvent<ObjectChangeNotificationBook> event) {
        add(event)
    }
}
