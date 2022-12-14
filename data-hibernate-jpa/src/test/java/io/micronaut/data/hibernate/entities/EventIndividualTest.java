package io.micronaut.data.hibernate.entities;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

// This Entity comes with no listeners to test the behavior of individual EntityEventListener
// interface implementations
@Entity
public class EventIndividualTest {

    @Id
    @GeneratedValue
    private Long id;
    @AutoPopulated
    @Column(columnDefinition = "uuid")
    private UUID uuid;

    @DateCreated
    private LocalDateTime dateCreated;

    @DateUpdated
    private LocalDateTime dateUpdated;

    @Column(name = "int_value")
    private int value;

    public EventIndividualTest(int value) {
        this.value = value;
    }

    public EventIndividualTest() {
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public LocalDateTime getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(LocalDateTime dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public void setValue(int value) { this.value = value; }

    public int getValue() { return value; }
}
