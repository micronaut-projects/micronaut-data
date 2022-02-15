
package example

import io.micronaut.data.annotation.Where
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id


@Entity
@Where("@.enabled = true") // <1>
data class User(
    @GeneratedValue
    @Id
    var id: Long,
    val name: String,
    val enabled: Boolean // <2>
)