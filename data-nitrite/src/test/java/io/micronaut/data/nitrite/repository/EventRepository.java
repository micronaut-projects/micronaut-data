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

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Event;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository for testing event patterns. */
/**
 * Repository that drives event-oriented test scenarios.
 */
@NitriteRepository
public interface EventRepository
    extends CrudRepository<Event, String>, PageableRepository<Event, String>, JpaSpecificationExecutor<Event> {

  /**
   * Finds events having the exact supplied type.
   *
   * @param type the event type
   * @return matching events
   */
  List<Event> findByType(String type);

  /**
   * Finds events whose type contains the provided keyword.
   *
   * @param keyword substring to match
   * @return matching events
   */
  List<Event> findByTypeContaining(String keyword);

  /**
   * Finds events with a priority greater than the supplied value.
   *
   * @param priority minimum priority (exclusive)
   * @return matching events
   */
  List<Event> findByPriorityGreaterThan(int priority);

  /**
   * Finds events with a priority less than or equal to the supplied value.
   *
   * @param priority maximum priority (inclusive)
   * @return matching events
   */
  List<Event> findByPriorityLessThanEquals(int priority);

  /**
   * Finds events whose payload field is null.
   *
   * @return matching events
   */
  List<Event> findByPayloadIsNull();

  /**
   * Finds events whose payload field is not null.
   *
   * @return matching events
   */
  List<Event> findByPayloadIsNotNull();

  /**
   * Finds events that have been marked as processed.
   *
   * @return processed events
   */
  List<Event> findByProcessedTrue();

  /**
   * Finds events whose payload is an empty string.
   *
   * @return matching events
   */
  List<Event> findByPayloadIsEmpty();

  /**
   * Finds events matching the supplied payload exactly.
   *
   * @param payload payload to match
   * @return matching events
   */
  List<Event> findByPayload(String payload);

  /**
   * Finds events whose payload contains the supplied keyword.
   *
   * @param keyword substring
   * @return matching events
   */
  List<Event> findByPayloadContaining(String keyword);

  /**
   * Finds events that occurred at the exact instant.
   *
   * @param occurredAt the time to match
   * @return matching events
   */
  List<Event> findByOccurredAt(Instant occurredAt);

  /**
   * Finds events that occurred strictly after the supplied instant.
   *
   * @param cutoff lower bound instant
   * @return matching events
   */
  List<Event> findByOccurredAtAfter(Instant cutoff);

  List<Event> findByTypeIn(Collection<String> types);

  List<Event> findByTypeNotIn(Collection<String> types);

  /**
   * KNOWN BUG: Uses @Query with field filter which silently returns empty in Nitrite.
   * The field filter does not work.
   */
  List<Event> findByTypeIgnoreCase(String type);

  List<Event> findByTypeNotIgnoreCase(String type);

  List<Event> findByPriorityBetween(int from, int to);

  @Query("{\"type\": {\"$eq\": :type}}")
  Optional<Event> findByTypeWithQuery(String type);

  // Aggregation methods for Phase 5 coverage (must have a By clause to match the aggregation pattern)
  Optional<Double> findMaxAmountByStatus(Event.Status status);
  Optional<Double> findMinAmountByStatus(Event.Status status);

  Optional<LocalDate> findMaxDateCreatedByStatus(Event.Status status);
  Optional<LocalDate> findMinDateCreatedByStatus(Event.Status status);

  // Status filter methods
  List<Event> findByStatus(Event.Status status);
  long countByStatus(Event.Status status);
}
