package example

import io.micronaut.data.annotation.event.PrePersist
import jakarta.inject.Singleton

@Singleton
class AccountUsernameValidator {
    @PrePersist
    fun validateUsername(account: Account) {
        val username: String = account.username
        require(username.matches("[a-z0-9]+".toRegex())) { "Invalid username" }
    }
}