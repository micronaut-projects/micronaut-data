// tag::join[]
// tag::async[]
package example;

import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.CrudRepository;
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
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
