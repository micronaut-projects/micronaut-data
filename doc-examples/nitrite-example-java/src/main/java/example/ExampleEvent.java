package example;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// tag::event-type-mapping[]
@MappedEntity
public class ExampleEvent {
    public enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    @Id
    @GeneratedValue
    private String id;

    private String type;
    private String payload;
    private Integer priority;
    private Status status;
    private LocalDate eventDate;
    private LocalDateTime eventDateTime;
    private Instant occurredAt;
    private BigDecimal amount;
    private Double score;
    private byte[] data;
    private List<String> tags;
    private Map<String, String> metadata;
    private Map<String, EventAttempt> attempts;
    private Optional<String> note;
    private EventLocation location;

    public ExampleEvent() {
    }

    public ExampleEvent(String type, String payload, Status status, BigDecimal amount) {
        this.type = type;
        this.payload = payload;
        this.status = status;
        this.amount = amount;
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalDateTime getEventDateTime() {
        return eventDateTime;
    }

    public void setEventDateTime(LocalDateTime eventDateTime) {
        this.eventDateTime = eventDateTime;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Map<String, EventAttempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(Map<String, EventAttempt> attempts) {
        this.attempts = attempts;
    }

    public Optional<String> getNote() {
        return note;
    }

    public void setNote(Optional<String> note) {
        this.note = note;
    }

    public EventLocation getLocation() {
        return location;
    }

    public void setLocation(EventLocation location) {
        this.location = location;
    }

    @Embeddable
    public static class EventLocation {
        private String region;
        private String zone;

        public EventLocation() {
        }

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

    @Introspected
    @Serdeable
    public static class EventAttempt {
        private String handler;
        private Integer retries;

        public EventAttempt() {
        }

        public EventAttempt(String handler, Integer retries) {
            this.handler = handler;
            this.retries = retries;
        }

        public String getHandler() {
            return handler;
        }

        public void setHandler(String handler) {
            this.handler = handler;
        }

        public Integer getRetries() {
            return retries;
        }

        public void setRetries(Integer retries) {
            this.retries = retries;
        }
    }
}
// end::event-type-mapping[]
