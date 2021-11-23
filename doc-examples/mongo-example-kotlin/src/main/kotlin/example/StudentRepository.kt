package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Version
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
import java.util.*

// tag::studentRepository[]
@MongoRepository
interface StudentRepository : CrudRepository<Student, ObjectId> {

    fun update(@Id id: ObjectId, @Version version: Long, name: String)

    fun delete(@Id id: ObjectId, @Version version: Long)

    // end::studentRepository[]
    @Join("courses")
    override fun findById(@NonNull id: ObjectId?): Optional<Student>

    @JoinSpecifications(
            Join("courses"),
            Join("ratings"),
            Join("ratings.course"),
            Join("ratings.student")
    )
    fun queryById(id: ObjectId): Optional<Student>
    // tag::studentRepository[]
}
// end::studentRepository[]