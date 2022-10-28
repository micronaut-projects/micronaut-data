package example

import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId

@MongoRepository
interface CourseRatingRepository : KotlinCrudRepository<CourseRating, ObjectId> {
}
