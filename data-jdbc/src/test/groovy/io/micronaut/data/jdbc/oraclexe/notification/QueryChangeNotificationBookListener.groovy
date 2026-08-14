package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection

@Singleton
@Requires(property = "query-notification.enabled")
class QueryChangeNotificationBookListener extends AbstractQueryNotificationBookListener<QueryChangeNotificationBook> {
    @ChangeListener
    @OracleChangeNotification(select = "id, title", where = "title = 'Query Change Notification'", properties = [
        @OracleChangeNotification.Property(name = OracleConnection.DCN_CLIENT_INIT_CONNECTION, value = "true"),
        @OracleChangeNotification.Property(name = OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, value = "true")
    ])
    void onBookChanged(ChangeEvent<QueryChangeNotificationBook> event) {
        add(event)
    }
}
