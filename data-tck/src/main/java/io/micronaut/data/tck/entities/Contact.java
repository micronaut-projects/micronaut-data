package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;

@MappedEntity(value = "TBL_CONTACT", alias = "c")
public class Contact {
    @Id
    private Long id;
    private String name;
    private int age;
    private boolean active = true;
    private LocalDateTime startDateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }
}
