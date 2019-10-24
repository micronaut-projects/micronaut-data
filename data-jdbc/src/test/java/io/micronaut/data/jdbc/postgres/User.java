package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "user_")
public class User {

    @Id
    @GeneratedValue(
            value = GeneratedValue.Type.SEQUENCE,
            ref = "user_seq")
    private Long id;

    private String username;

    public User(String username) {
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}