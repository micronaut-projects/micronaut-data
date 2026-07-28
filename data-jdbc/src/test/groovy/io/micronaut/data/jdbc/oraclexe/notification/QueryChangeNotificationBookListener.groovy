package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection

@Singleton
@Requires(property = "query-notification.enabled")
class QueryChangeNotificationBookListener extends AbstractQueryNotificationBookListener<QueryChangeNotificationBook> {
    @ChangeListener(select = "id, title", where = "title = 'Query Change Notification'", properties = [
        @ChangeListener.Property(name = OracleConnection.DCN_CLIENT_INIT_CONNECTION, value = "true"),
        @ChangeListener.Property(name = OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, value = "true")
    ])
    void onBookChanged(QueryChangeNotificationBook book) {
        add(book)
    }
}
