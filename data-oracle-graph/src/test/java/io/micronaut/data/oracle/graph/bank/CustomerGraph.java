package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;
import java.util.Optional;

@OracleGraphRepository(graph = "customer_graph")
public interface CustomerGraph extends GenericRepository<Customer, Long> {

    Optional<Customer> findById(Long id);

    List<Customer> findByReferrerName(String name);

    Customer save(Customer customer);

    List<Customer> saveAll(Iterable<Customer> customers);

    Customer update(Customer customer);

    List<Customer> updateAll(Iterable<Customer> customers);

    String updateReturningName(Customer customer);

    String updateReturningName(@Id Long id, String name);

    String deleteReturningName(Customer customer);

    void deleteById(Long id);

    long count();
}
