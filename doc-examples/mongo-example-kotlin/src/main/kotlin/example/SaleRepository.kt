
package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.mongodb.annotation.MongoAggregateOptions
import io.micronaut.data.mongodb.annotation.MongoCollation
import io.micronaut.data.mongodb.annotation.MongoFindOptions
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId
import java.util.*

// tag::options[]
@MongoFindOptions(allowDiskUse = true, maxTimeMS = 1000)
@MongoAggregateOptions(allowDiskUse = true, maxTimeMS = 100)
@MongoCollation("{ locale: 'en_US', numericOrdering: true}")
@MongoRepository
interface SaleRepository : KotlinCrudRepository<Sale, ObjectId> {
// end::options[]

    @JoinSpecifications(value = [Join("product"), Join("product.manufacturer")])
    override fun findById(id: ObjectId): Sale?
}
