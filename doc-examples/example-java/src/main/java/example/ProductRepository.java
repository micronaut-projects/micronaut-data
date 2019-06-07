// tag::join[]
// tag::async[]
package example;

import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.CrudRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {
// end::join[]
// end::async[]
    // tag::join[]
    Manufacturer saveManufacturer(String name);

    @JoinSpec("manufacturer") // <1>
    List<Product> list();
    // end::join[]

    // tag::async[]
    CompletableFuture<Product> findByNameContains(String str);

    CompletableFuture<Long> countByManufacturerName(String name);
    // end::async[]
    // tag::reactive[]
    Maybe<Product> queryByNameContains(String str);

    Single<Long> countDistinctByManufacturerName(String name);
    // end::reactive[]
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
