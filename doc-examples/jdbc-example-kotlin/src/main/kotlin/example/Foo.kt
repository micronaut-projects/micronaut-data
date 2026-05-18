package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

@MappedEntity
data class Foo(
    @field:Id
    @GeneratedValue
    val id: Long? = null,

    val name: String? = null,

    @Relation(value = Relation.Kind.ONE_TO_ONE)
    val foo: Foo? = null,

    @Relation(value = Relation.Kind.ONE_TO_ONE)
    val bar: Bar? = null,
)
