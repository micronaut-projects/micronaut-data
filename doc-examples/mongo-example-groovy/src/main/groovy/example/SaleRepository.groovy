
package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId

import javax.validation.constraints.NotNull

@MongoRepository
interface SaleRepository extends CrudRepository<Sale, ObjectId> {

    @NonNull
    @Override
    @Join("product")
    @Join("product.manufacturer")
    Optional<Sale> findById(@NonNull @NotNull ObjectId id)
}
