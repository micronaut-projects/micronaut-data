package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
data class Course(
        @field:Id @GeneratedValue
        val id: ObjectId?,
        val name: String,
        @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
        val students: List<Student>
) {
    constructor(name: String) : this(null, name, emptyList())
}
