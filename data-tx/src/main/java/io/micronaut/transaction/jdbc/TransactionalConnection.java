package io.micronaut.transaction.jdbc;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Allows injecting a {@link Connection} instance as a bean with any methods invoked
 * on the connection being delegated to connection bound to the current transaction.
 *
 * <p>If no transaction is </p>
 * @author graemerocher
 * @since 1.0
 */
@EachBean(DataSource.class)
@TransactionalConnectionAdvice
@Internal
public interface TransactionalConnection extends Connection {

}
