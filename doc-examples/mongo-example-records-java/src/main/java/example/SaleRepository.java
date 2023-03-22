
package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.mongodb.annotation.MongoAggregateOptions;
import io.micronaut.data.mongodb.annotation.MongoCollation;
import io.micronaut.data.mongodb.annotation.MongoFindOptions;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;

// tag::options[]
@MongoFindOptions(allowDiskUse = true, maxTimeMS = 1000)
@MongoAggregateOptions(allowDiskUse = true, maxTimeMS = 100)
@MongoCollation("{ locale: 'en_US', numericOrdering: true}")
@MongoRepository
public interface SaleRepository extends CrudRepository<Sale, ObjectId> {
// end::options[]

    @NonNull
    @Override
    @Join("product")
    @Join("product.manufacturer")
    Optional<Sale> findById(@NonNull @NotNull ObjectId id);

    @Join("product")
    @Join("product.manufacturer")
    Optional<Sale> findByQuantity(@NonNull @NotNull Quantity quantity);
}
