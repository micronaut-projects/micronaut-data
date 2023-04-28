package io.micronaut.data.connection.jdbc;

import io.micronaut.data.connection.manager.ConnectionDefinition;

import java.util.Optional;

public interface JdbcConnectionDefinition extends ConnectionDefinition {

    JdbcConnectionDefinition DEFAULT = JdbcConnectionDefinition.of(ConnectionDefinition.DEFAULT);

    JdbcConnectionDefinition READ_ONLY = JdbcConnectionDefinition.of(ConnectionDefinition.READ_ONLY);

    JdbcConnectionDefinition TRANSACTION = JdbcConnectionDefinition.of(ConnectionDefinition.DEFAULT).withAutoCommit(false);

    Optional<Boolean> autoCommit();

    JdbcConnectionDefinition readOnly();

    JdbcConnectionDefinition withAutoCommit(boolean autoCommit);

    static JdbcConnectionDefinition of(ConnectionDefinition connectionDefinition) {
        return new DefaultJdbcConnectionDefinition(connectionDefinition, null);
    }
}
