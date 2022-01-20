
package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.mongo.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import javax.validation.constraints.NotNull;
import java.util.Optional;

@MongoRepository
public interface SaleRepository extends CrudRepository<Sale, ObjectId> {

    @NonNull
    @Override
    @Join("product")
    @Join("product.manufacturer")
    Optional<Sale> findById(@NonNull @NotNull ObjectId id);

    @Join("product")
    @Join("product.manufacturer")
    Optional<Sale> findByQuantity(@NonNull @NotNull Quantity quantity);
}
