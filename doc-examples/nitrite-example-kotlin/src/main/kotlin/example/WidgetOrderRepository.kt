package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.util.UUID

// tag::uuid-widget-order-repository[]
@NitriteRepository
interface WidgetOrderRepository : CrudRepository<WidgetOrder, UUID> {
    @Query("{\"widget\": {\"\$eq\": :value}}")
    fun findByWidgetValue(value: String): List<WidgetOrder>
}
// end::uuid-widget-order-repository[]
