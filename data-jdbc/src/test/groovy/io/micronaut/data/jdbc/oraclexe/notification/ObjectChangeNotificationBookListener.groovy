package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection

@Singleton
@Requires(property = "query-notification.enabled")
class ObjectChangeNotificationBookListener extends AbstractQueryNotificationBookListener<ObjectChangeNotificationBook> {
    @ChangeListener(properties = [
        @ChangeListener.Property(name = OracleConnection.DCN_CLIENT_INIT_CONNECTION, value = "true")
    ])
    void onBookChanged(ObjectChangeNotificationBook book) {
        add(book)
    }
}
