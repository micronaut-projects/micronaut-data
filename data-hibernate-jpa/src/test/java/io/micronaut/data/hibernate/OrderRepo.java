package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.repositories.OrderRepository;

import java.math.BigDecimal;

@Repository
public interface OrderRepo extends OrderRepository {
    BigDecimal findSumTotalAmountByCustomer(String customer);
    Double findSumWeightByCustomer(String customer);
    Integer findSumUnitsByCustomer(String customer);
    Long findSumTaxByCustomer(String customer);
}
