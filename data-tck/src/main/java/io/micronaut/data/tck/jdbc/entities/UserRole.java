package io.micronaut.data.tck.jdbc.entities;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;

@MappedEntity("user_role_composite")
public class UserRole {

    @EmbeddedId
    private final UserRoleId id;

    public UserRole(UserRoleId id) {
        this.id = id;
    }

    public UserRoleId getId() {
        return id;
    }

    @Transient
    public User getUser() {
        return id.getUser();
    }

    @Transient
    public Role getRole() {
        return id.getRole();
    }
}
