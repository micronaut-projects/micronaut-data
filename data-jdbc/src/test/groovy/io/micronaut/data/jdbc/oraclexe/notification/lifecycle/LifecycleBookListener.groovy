package io.micronaut.data.jdbc.oraclexe.notification.lifecycle

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.jdbc.oraclexe.notification.AbstractChangeListener
import jakarta.inject.Singleton

@Singleton
@Requires(property = 'oracle-lifecycle.enabled', value = 'true')
class LifecycleBookListener extends AbstractChangeListener<LifecycleBook> {
    @ChangeListener
    @OracleChangeNotification
    void onChange(ChangeEvent<LifecycleBook> event) {
        add(event)
    }
}
