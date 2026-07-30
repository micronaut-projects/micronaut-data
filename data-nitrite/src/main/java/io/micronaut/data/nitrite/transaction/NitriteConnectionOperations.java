package io.micronaut.data.nitrite.transaction;

import io.micronaut.context.annotation.Primary;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import jakarta.inject.Singleton;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.transaction.Session;

/**
 * Connection operations for Nitrite.
 *
 * <p>Extends {@link AbstractConnectionOperations} to track connections via Micronaut's propagation
 * mechanisms (PropagatedContext), enabling correct session management and transaction propagation.
 *
 * <p><strong>Important Design Note:</strong> This class is stateless. It does NOT use a ThreadLocal
 * for connection tracking; instead, it relies on the base class implementation which uses
 * PropagatedContext. This ensures consistent session state across propagated or nested
 * transactions.
 *
 * @since 1.0.0
 */
@Singleton
@Primary
public class NitriteConnectionOperations extends AbstractConnectionOperations<Session>
    implements SynchronousConnectionManager<Session> {

  private final Nitrite database;

  /**
   * Create a new Nitrite connection operations.
   *
   * @param database the Nitrite database
   */
  public NitriteConnectionOperations(Nitrite database) {
    this.database = database;
  }

  @Override
  protected Session openConnection(ConnectionDefinition definition) {
    return database.createSession();
  }

  @Override
  protected void setupConnection(ConnectionStatus<Session> connectionStatus) {
    // no-op
  }

  @Override
  protected void closeConnection(ConnectionStatus<Session> connectionStatus) {
    Session session = connectionStatus.getConnection();
      session.close();
  }
}
