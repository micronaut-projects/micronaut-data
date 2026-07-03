package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.repositories.PurchaseOrderRepository;

@Repository
public interface JpaPurchaseOrderRepository extends PurchaseOrderRepository {
}
