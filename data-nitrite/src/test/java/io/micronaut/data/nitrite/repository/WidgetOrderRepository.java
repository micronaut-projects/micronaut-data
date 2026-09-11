package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.WidgetOrder;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@NitriteRepository
public interface WidgetOrderRepository extends CrudRepository<WidgetOrder, UUID> {

    // Raw @Query putting a non-UUID String on the MANY_TO_ONE "widget" field forces
    // AssociationFilterResolver.buildForwardLookupFilter (sub-query over Widget's
    // String properties) at runtime: useSubQuery = !looksLikeId on a UUID identity.
    // A derived findByWidget(String) cannot express this — the AP rejects a String
    // param for a Widget-typed property.
    @Query("{\"widget\": {\"$eq\": :name}}")
    List<WidgetOrder> findByWidgetStringValue(String name);
}
