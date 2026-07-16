package io.micronaut.data.hibernate.entities;

import io.micronaut.core.annotation.Introspected;

import java.util.UUID;

@Introspected
public class UserWithWhereSummary {

    private final UUID id;
    private final String email;
    private final Boolean deleted;

    public UserWithWhereSummary(UUID id, String email, Boolean deleted) {
        this.id = id;
        this.email = email;
        this.deleted = deleted;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Boolean getDeleted() {
        return deleted;
    }
}
