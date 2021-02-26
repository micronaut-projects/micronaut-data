package example

import io.micronaut.data.annotation.event.PrePersist

import javax.inject.Singleton

@Singleton
class AccountUsernameValidator {
    @PrePersist
    void validateUsername(Account account) {
        final String username = account.username
        if (!username || !(username ==~ /[a-z0-9]+/)) {
            throw new IllegalArgumentException("Invalid username")
        }
    }
}
