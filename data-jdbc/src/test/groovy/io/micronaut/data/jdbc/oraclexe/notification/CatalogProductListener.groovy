package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection

@Singleton
@Requires(property = "query-notification.enabled")
class CatalogProductListener extends AbstractChangeListener<CatalogProduct> {
    @ChangeListener
    @OracleChangeNotification(select = "id, category_id", where = '''category_id IN (
        SELECT id FROM TEST.CATALOG_CATEGORY WHERE enabled = 1
    )''', properties = [
        @OracleChangeNotification.Property(name = OracleConnection.DCN_CLIENT_INIT_CONNECTION, value = "true"),
        @OracleChangeNotification.Property(name = OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, value = "true"),
        @OracleChangeNotification.Property(name = OracleConnection.DCN_BEST_EFFORT, value = "true")
    ])
    void onProductChanged(ChangeEvent<CatalogProduct> event) {
        add(event)
    }
}
