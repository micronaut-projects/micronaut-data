package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Version
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId
import java.util.*

// tag::studentRepository[]
@MongoRepository
interface StudentRepository : KotlinCrudRepository<Student, ObjectId> {

    fun update(@Id id: ObjectId, @Version version: Long, name: String)

    fun delete(@Id id: ObjectId, @Version version: Long)

    // end::studentRepository[]
    @Join("courses")
    override fun findById(id: ObjectId): Student?

    @JoinSpecifications(
            Join("courses"),
            Join("ratings"),
            Join("ratings.course"),
            Join("ratings.student")
    )
    fun queryById(id: ObjectId): Student?
    // tag::studentRepository[]
}
// end::studentRepository[]
