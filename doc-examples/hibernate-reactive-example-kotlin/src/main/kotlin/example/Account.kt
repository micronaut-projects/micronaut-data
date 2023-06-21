package example

import java.nio.charset.StandardCharsets
import java.util.*
import jakarta.persistence.*

@Entity
data class Account(@GeneratedValue @Id
                   var id: Long? = null,
                   val username: String,
                   var password: String) {

    @PrePersist
    fun encodePassword() {
        password = Base64.getEncoder()
            .encodeToString(password.toByteArray(StandardCharsets.UTF_8))
    }
}
