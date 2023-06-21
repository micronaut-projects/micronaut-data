
package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoAggregateOptions
import io.micronaut.data.mongodb.annotation.MongoCollation
import io.micronaut.data.mongodb.annotation.MongoFindOptions
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId

import jakarta.validation.constraints.NotNull

// tag::options[]
@MongoFindOptions(allowDiskUse = true, maxTimeMS = 1000L)
@MongoAggregateOptions(allowDiskUse = true, maxTimeMS = 100L)
@MongoCollation("{ locale: 'en_US', numericOrdering: true}")
@MongoRepository
interface SaleRepository extends CrudRepository<Sale, ObjectId> {
// end::options[]

    @NonNull
    @Override
    @Join("product")
    @Join("product.manufacturer")
    Optional<Sale> findById(@NonNull @NotNull ObjectId id)
}
