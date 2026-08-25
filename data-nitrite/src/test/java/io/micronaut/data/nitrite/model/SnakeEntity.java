package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity("snake_entity")
public class SnakeEntity {
    @Id
    private Long id;

    @MappedProperty("session_id")
    private String sessionId;

    private Integer level;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
}
