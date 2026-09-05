/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.transaction;

import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
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
 * @since 5.2.0
 */
public class NitriteConnectionOperations extends AbstractConnectionOperations<Session>
    implements SynchronousConnectionManager<Session> {

  private final Nitrite database;

  /**
   * Create connection operations bound to one datasource's database.
   *
   * @param database the Nitrite database of this datasource
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
