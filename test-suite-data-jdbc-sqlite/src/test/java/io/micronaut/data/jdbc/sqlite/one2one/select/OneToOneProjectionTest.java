package io.micronaut.data.jdbc.sqlite.one2one.select;

import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@MicronautTest(transactional = false)
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2one.select")
class OneToOneProjectionTest {

    @Inject
    MyOrderRepository orderRepository;

    @Test
    void findAllWithPageableSortAndSearch() {
        Sort.Order.Direction sortDirection = Sort.Order.Direction.ASC;
        Pageable pageable = Pageable.UNPAGED.order(new Sort.Order("embedded.someProp", sortDirection, false));
        PredicateSpecification<MyOrder> predicate = null;

        assertDoesNotThrow(() -> orderRepository.findAll(predicate, pageable));
    }
}
