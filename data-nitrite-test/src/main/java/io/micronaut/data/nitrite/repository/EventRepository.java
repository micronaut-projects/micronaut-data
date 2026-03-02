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
package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Event;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import java.time.Instant;
import java.util.List;

/** Repository for testing event patterns. */
@NitriteRepository
public interface EventRepository
    extends CrudRepository<Event, String>, PageableRepository<Event, String> {

  // Basic queries
  List<Event> findByType(String type);

  List<Event> findByTypeContaining(String keyword);

  // Integer comparisons
  List<Event> findByPriorityGreaterThan(int priority);

  List<Event> findByPriorityLessThanEquals(int priority);

  // Null checks
  List<Event> findByPayloadIsNull();

  List<Event> findByPayloadIsNotNull();

  // Boolean checks
  List<Event> findByProcessedTrue();

  // Empty string check
  List<Event> findByPayloadIsEmpty();

  // Exact match
  List<Event> findByPayload(String payload);

  // Contains
  List<Event> findByPayloadContaining(String keyword);

  // Instant queries
  List<Event> findByOccurredAt(Instant occurredAt);

  List<Event> findByOccurredAtAfter(Instant cutoff);
}
