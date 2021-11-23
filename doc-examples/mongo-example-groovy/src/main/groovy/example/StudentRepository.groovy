package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Version
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
// tag::studentRepository[]
@MongoRepository
interface StudentRepository extends CrudRepository<Student, ObjectId> {

    void update(@Id ObjectId id, @Version Long version, String name)

    void delete(@Id ObjectId id, @Version Long version)
}
// end::studentRepository[]


