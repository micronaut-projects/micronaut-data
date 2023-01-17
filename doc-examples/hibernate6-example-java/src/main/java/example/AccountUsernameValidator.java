package example;

import io.micronaut.data.annotation.event.PrePersist;

import jakarta.inject.Singleton;

@Singleton
public class AccountUsernameValidator {
    @PrePersist
    void validateUsername(Account account) {
        final String username = account.getUsername();
        if (username == null || !username.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("Invalid username");
        }
    }
}
