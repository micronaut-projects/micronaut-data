package example

import io.micronaut.data.annotation.*

@MappedEntity
@Where("enabled = true") // <1>
data class User(
    @GeneratedValue
    @Id
    var id: Long? = null,
    val name: String,
    val enabled: Boolean // <2>
)