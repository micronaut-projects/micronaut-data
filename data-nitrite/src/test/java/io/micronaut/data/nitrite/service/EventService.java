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
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/** Service used to exercise @Transactional behaviour in tests. */
@Singleton
public class EventService {

  private final EventRepository repo;

  /**
   * Create a new event service.
   *
   * @param repo the event repository
   */
  public EventService(EventRepository repo) {
    this.repo = repo;
  }

  /**
   * Save an event with REQUIRES_NEW propagation.
   *
   * @param event the event to save
   * @return the saved event
   */
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public Event saveEvent(Event event) {
    return repo.save(event);
  }

  /**
   * Saves an event then throws — the save must be rolled back.
   *
   * @param event the event to save
   */
  @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
  public void saveAndFail(Event event) {
    repo.save(event);
    throw new RuntimeException("deliberate failure");
  }
}
