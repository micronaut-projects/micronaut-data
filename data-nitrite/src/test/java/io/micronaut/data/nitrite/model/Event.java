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

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Entity for testing event patterns with diverse field types. */
@MappedEntity("events")
public class Event {
  public enum Status {
    ACTIVE,
    INACTIVE,
    PENDING
  }

  @Id @GeneratedValue private String id;

  private String type;
  private String payload;
  private Integer priority;
  private Boolean processed;
  private Instant occurredAt;
  private List<String> tags;
  private Status status;
  private LocalDate dateCreated;
  private LocalDateTime dateTimeCreated;
  private BigDecimal amount;
  private byte[] data;
  private Map<String, String> metadata;
  private Optional<String> optionalDescription;
  private EventLocation location;

  public Event() {}

  public Event(String type, String payload) {
    this.type = type;
    this.payload = payload;
    this.priority = 5;
    this.processed = false;
  }

  public Event(String type, String payload, Status status, LocalDate dateCreated,
               LocalDateTime dateTimeCreated, Instant occurredAt, BigDecimal amount,
               byte[] data, List<String> tags, Map<String, String> metadata,
               Optional<String> optionalDescription, EventLocation location) {
    this.type = type;
    this.payload = payload;
    this.status = status;
    this.dateCreated = dateCreated;
    this.dateTimeCreated = dateTimeCreated;
    this.occurredAt = occurredAt;
    this.amount = amount;
    this.data = data;
    this.tags = tags;
    this.metadata = metadata;
    this.optionalDescription = optionalDescription;
    this.location = location;
    this.priority = 5;
    this.processed = false;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public Boolean isProcessed() {
    return processed;
  }

  public void setProcessed(Boolean processed) {
    this.processed = processed;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public LocalDate getDateCreated() {
    return dateCreated;
  }

  public void setDateCreated(LocalDate dateCreated) {
    this.dateCreated = dateCreated;
  }

  public LocalDateTime getDateTimeCreated() {
    return dateTimeCreated;
  }

  public void setDateTimeCreated(LocalDateTime dateTimeCreated) {
    this.dateTimeCreated = dateTimeCreated;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public byte[] getData() {
    return data;
  }

  public void setData(byte[] data) {
    this.data = data;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public Optional<String> getOptionalDescription() {
    return optionalDescription;
  }

  public void setOptionalDescription(Optional<String> optionalDescription) {
    this.optionalDescription = optionalDescription;
  }

  public EventLocation getLocation() {
    return location;
  }

  public void setLocation(EventLocation location) {
    this.location = location;
  }

  @Embeddable
  public static class EventLocation implements Serializable {
    private String region;
    private String zone;

    public EventLocation() {}

    public EventLocation(String region, String zone) {
      this.region = region;
      this.zone = zone;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getZone() {
      return zone;
    }

    public void setZone(String zone) {
      this.zone = zone;
    }
  }
}
