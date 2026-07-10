package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

// tag::uuid-widget-order-repository[]
@NitriteRepository
public interface WidgetOrderRepository extends CrudRepository<WidgetOrder, UUID> {
    @Query("""
    {"widget": {"$eq": :value}}
    """)
    List<WidgetOrder> findByWidgetValue(String value);
}
// end::uuid-widget-order-repository[]
