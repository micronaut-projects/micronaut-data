
package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Where


@MappedEntity
@Where("@.enabled = true") // <1>
data class User(
    @GeneratedValue
    @field:Id
    var id: Long,
    val name: String,
    val enabled: Boolean // <2>
)
