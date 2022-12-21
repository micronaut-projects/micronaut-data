package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@MappedEntity
data class Book(
        val title: String,
        val pages: Int,
        @Relation(Relation.Kind.MANY_TO_ONE)
        val author: Author) {
    @Id
    @GeneratedValue
    var id: Long? = null
}
