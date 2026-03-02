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
package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;

/** Entity for testing event patterns with simple fields. */
@MappedEntity("events")
public class Event {
  @Id @GeneratedValue private String id;

  private String type;
  private String payload;
  private Integer priority;
  private Boolean processed;
  private Instant occurredAt;

  public Event() { }

  public Event(String type, String payload) {
    this.type = type;
    this.payload = payload;
    this.priority = 5;
    this.processed = false;
  }

  /**
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * @param id the id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * @return the type
   */
  public String getType() {
    return type;
  }

  /**
   * @param type the type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * @return the payload
   */
  public String getPayload() {
    return payload;
  }

  /**
   * @param payload the payload
   */
  public void setPayload(String payload) {
    this.payload = payload;
  }

  /**
   * @return the priority
   */
  public Integer getPriority() {
    return priority;
  }

  /**
   * @param priority the priority
   */
  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  /**
   * @return is processed
   */
  public Boolean isProcessed() {
    return processed;
  }

  /**
   * @param processed is processed
   */
  public void setProcessed(Boolean processed) {
    this.processed = processed;
  }

  /**
   * @return the occurred at
   */
  public Instant getOccurredAt() {
    return occurredAt;
  }

  /**
   * @param occurredAt the occurred at
   */
  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }
}
