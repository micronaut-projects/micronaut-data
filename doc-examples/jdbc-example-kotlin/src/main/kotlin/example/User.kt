package example

import io.micronaut.data.annotation.Where
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id


@Entity
@Where("enabled = true") // <1>
data class User(
    @GeneratedValue
    @Id
    var id: Long,
    val name: String,
    val enabled: Boolean // <2>
)