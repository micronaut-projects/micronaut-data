package io.micronaut.data.jdbc.oraclexe.notification.lifecycle

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.jdbc.oraclexe.notification.AbstractChangeListener
import jakarta.inject.Singleton

@Singleton
@Requires(property = 'oracle-lifecycle.query', value = 'true')
class QueryLifecycleBookListener extends AbstractChangeListener<LifecycleBook> {
    @ChangeListener
    @OracleChangeNotification(properties = [@OracleChangeNotification.Property(name = 'DCN_QUERY_CHANGE_NOTIFICATION', value = 'true')])
    void onChange(ChangeEvent<LifecycleBook> event) {
        add(event)
    }
}
