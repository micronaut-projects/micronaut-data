package example

import jakarta.persistence.*
import java.nio.charset.StandardCharsets

@Entity
class Account {
    @GeneratedValue
    @Id
    Long id
    String username
    String password

    @PrePersist
    void encodePassword() {
        this.password = Base64.encoder
                .encodeToString(this.password.getBytes(StandardCharsets.UTF_8))
    }
}
