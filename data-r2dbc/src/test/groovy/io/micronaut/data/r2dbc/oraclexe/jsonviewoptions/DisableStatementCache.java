package io.micronaut.data.r2dbc.oraclexe.jsonviewoptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import jakarta.inject.Singleton;

/**
 * Turns off the implicit statement cache of Oracle R2DBC: Oracle returns a wrong document from a cached query
 * after an update through a duality view.
 */
@Singleton
@Requires(property = "spec.name", value = "OracleR2dbcJsonViewOptionsSpec")
class DisableStatementCache implements BeanCreatedEventListener<ConnectionFactoryOptions.Builder> {

    @Override
    public ConnectionFactoryOptions.Builder onCreated(BeanCreatedEvent<ConnectionFactoryOptions.Builder> event) {
        return event.getBean().option(Option.valueOf("oracle.jdbc.implicitStatementCacheSize"), 0);
    }
}
