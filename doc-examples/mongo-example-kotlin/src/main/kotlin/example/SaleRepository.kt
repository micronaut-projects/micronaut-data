
package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId

import java.util.Optional

@MongoRepository
interface SaleRepository : CrudRepository<Sale, ObjectId> {

    @JoinSpecifications(value = [Join("product"), Join("product.manufacturer")])
    override fun findById(id: ObjectId): Optional<Sale>
}
