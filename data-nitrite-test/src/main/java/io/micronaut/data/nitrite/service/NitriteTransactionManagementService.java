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
package io.micronaut.data.nitrite.service;

import io.micronaut.data.nitrite.model.Event;
import io.micronaut.data.nitrite.repository.EventRepository;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * Service for testing Nitrite transaction management with various propagation modes.
 *
 * @since 1.0.0
 */
@Singleton
public class NitriteTransactionManagementService {

  private final EventRepository eventRepository;
  private final TransactionOperations<?> transactionOperations;

  /**
   * Create a new transaction management service.
   *
   * @param eventRepository the event repository
   * @param transactionOperations the transaction operations
   */
  public NitriteTransactionManagementService(
      EventRepository eventRepository, TransactionOperations<?> transactionOperations) {
    this.eventRepository = eventRepository;
    this.transactionOperations = transactionOperations;
  }

  /**
   * Save an event with default transaction propagation (REQUIRED).
   *
   * @param type the event type
   */
  @Transactional
  public void doSuccess(String type) {
    eventRepository.save(new Event(type, "success"));
  }

  /**
   * Save an event then throw an exception to trigger rollback.
   *
   * @param type the event type
   */
  @Transactional
  public void doFail(String type) {
    eventRepository.save(new Event(type, "fail"));
    throw new RuntimeException("Forced failure");
  }

  /**
   * Save an event with MANDATORY propagation (requires existing transaction).
   *
   * @param type the event type
   */
  @Transactional(propagation = TransactionDefinition.Propagation.MANDATORY)
  public void doMandatory(String type) {
    eventRepository.save(new Event(type, "mandatory"));
  }

  /**
   * Save an event with REQUIRES_NEW propagation (always creates new transaction).
   *
   * @param type the event type
   */
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public void doRequiresNew(String type) {
    eventRepository.save(new Event(type, "requires_new"));
  }

  /**
   * Save an event with NOT_SUPPORTED propagation (runs without transaction).
   *
   * @param type the event type
   */
  @Transactional(propagation = TransactionDefinition.Propagation.NOT_SUPPORTED)
  public void doNotSupported(String type) {
    transactionOperations
        .findTransactionStatus()
        .ifPresent(
            status -> {
              if (status.getTransaction() != null) {
                throw new IllegalStateException("Transaction should NOT be active");
              }
            });
    eventRepository.save(new Event(type, "not_supported"));
  }

  /**
   * Save an event with NEVER propagation (must not run in transaction).
   *
   * @param type the event type
   */
  @Transactional(propagation = TransactionDefinition.Propagation.NEVER)
  public void doNever(String type) {
    transactionOperations
        .findTransactionStatus()
        .ifPresent(
            status -> {
              if (status.getTransaction() != null) {
                throw new IllegalStateException("Transaction should NOT be active");
              }
            });
    eventRepository.save(new Event(type, "never"));
  }

  /**
   * Save an event then manually mark transaction for rollback.
   *
   * @param type the event type
   */
  @Transactional
  public void setRollbackOnlyManually(String type) {
    eventRepository.save(new Event(type, "manual_rollback"));
    transactionOperations.executeWrite(
        status -> {
          status.setRollbackOnly();
          return null;
        });
  }

  /**
   * Count all events.
   *
   * @return the event count
   */
  @Transactional
  public long countEvents() {
    return eventRepository.count();
  }

  /** Delete all events. */
  public void cleanup() {
    eventRepository.deleteAll();
  }
}
