package example

import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import org.bson.types.ObjectId

@MongoRepository
interface PersonSuspendRepository : CoroutineCrudRepository<Person, ObjectId>, CoroutineJpaSpecificationExecutor<Person>
