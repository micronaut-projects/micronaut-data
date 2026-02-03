package example

import io.micronaut.data.annotation.Where
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Entity
@Where("@.enabled = true")
class User {
    @GeneratedValue
    @Id
    Long id
    String name
    boolean enabled = true
}
