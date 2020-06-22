package example;

import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {

    @Join("manufacturer") // <1>
    List<Product> list();
}
