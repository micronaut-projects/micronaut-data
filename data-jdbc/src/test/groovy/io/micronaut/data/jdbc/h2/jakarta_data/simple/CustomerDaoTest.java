package io.micronaut.data.jdbc.h2.jakarta_data.simple;

import io.micronaut.data.jdbc.h2.H2DBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.data.Limit;
import jakarta.data.Sort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@H2DBProperties
@MicronautTest(startApplication = false)
class CustomerDaoTest {

    @Test
    public void testInsertAndQuery(CustomerRepository customerRepository) {
        var savedCustomer = customerRepository.save(Customer.of("customer_test", 20, Address.of("test", "NY", "210000")));
        assertNotNull(savedCustomer);
        var found = customerRepository.findById(savedCustomer.id());
        assertTrue(found.isPresent());
        found.ifPresent(it -> assertEquals("customer_test", it.name()));
        customerRepository.deleteById(savedCustomer.id());
        assertTrue(customerRepository.findById(savedCustomer.id()).isEmpty());
        customerRepository.deleteAll();
    }

    @Test
    public void testSaveChoosesInsertOrUpdate(CustomerRepository customerRepository) {
        var inserted = customerRepository.saveOrUpdate(Customer.of("customer_insert", 20, Address.of("test", "NY", "210000")));
        assertNotNull(inserted.id());

        var updated = customerRepository.saveOrUpdate(new Customer(
            inserted.id(),
            "customer_update",
            21,
            Address.of("test2", "LA", "220000"),
            inserted.version()
        ));

        assertEquals(inserted.id(), updated.id());
        var found = customerRepository.findById(inserted.id()).orElseThrow();
        assertEquals("customer_update", found.name());
        assertEquals(21, found.age());
        assertEquals("LA", found.address().city());
        customerRepository.deleteAll();
    }

    @Test
    public void testSaveAllChoosesInsertOrUpdate(CustomerRepository customerRepository) {
        var existing = customerRepository.saveOrUpdate(Customer.of("customer_existing", 30, Address.of("test", "NY", "210000")));
        var update = new Customer(
            existing.id(),
            "customer_existing_updated",
            31,
            Address.of("test3", "SF", "230000"),
            existing.version()
        );

        var saved = customerRepository.saveOrUpdateAll(List.of(
            Customer.of("customer_new_1", 20, Address.of("test1", "NY", "210000")),
            update,
            Customer.of("customer_new_2", 22, Address.of("test2", "LA", "220000"))
        ));

        assertEquals(3, saved.size());
        assertEquals("customer_new_1", saved.get(0).name());
        assertEquals(existing.id(), saved.get(1).id());
        assertEquals("customer_existing_updated", saved.get(1).name());
        assertEquals("customer_new_2", saved.get(2).name());
        assertEquals("customer_existing_updated", customerRepository.findById(existing.id()).orElseThrow().name());
        customerRepository.deleteAll();
    }

    @Test
    public void testInsertAndQuery2(CustomerRepository customerRepository, CustomerOnlyDeleteQueryRepository onlyDeleteQueryRepository) {
        var savedCustomer = customerRepository.save(Customer.of("customer_test", 20, Address.of("test", "NY", "210000")));
        assertNotNull(savedCustomer);
        var found = customerRepository.findById(savedCustomer.id());
        assertTrue(found.isPresent());
        found.ifPresent(it -> assertEquals("customer_test", it.name()));
        onlyDeleteQueryRepository.deleteById(savedCustomer.id());
        assertTrue(customerRepository.findById(savedCustomer.id()).isEmpty());
        customerRepository.deleteAll();
    }

    @Test
    public void testFind(CustomerRepository customerRepository) {
        customerRepository.save(Customer.of("Dcustomer_test1", 20, Address.of("test1", "NY", "210000")));
        customerRepository.save(Customer.of("Ccustomer_test2", 20, Address.of("test2", "NY", "210000")));
        customerRepository.save(Customer.of("Bcustomer_test3", 20, Address.of("test3", "NY", "210000")));
        customerRepository.save(Customer.of("Acustomer_test4", 20, Address.of("test4", "XY", "210000")));
        var found = customerRepository.findByCity("NY", Limit.of(3), Sort.asc("name"));
        assertEquals(3, found.size());
        assertEquals("Bcustomer_test3", found.get(0).name());
        assertEquals("Ccustomer_test2", found.get(1).name());
        assertEquals("Dcustomer_test1", found.get(2).name());
        customerRepository.deleteAll();
    }

    @Test
    public void deductEntityType(CustomerRepository customerRepository, CustomerRepository2 customerRepository2) {
        customerRepository.save(Customer.of("Dcustomer_test1", 20, Address.of("test1", "NY", "210000")));
        Customer customer1 = customerRepository.save(Customer.of("Ccustomer_test2", 20, Address.of("test2", "NY", "210000")));
        customerRepository.save(Customer.of("Bcustomer_test3", 20, Address.of("test3", "NY", "210000")));
        customerRepository.save(Customer.of("Acustomer_test4", 20, Address.of("test4", "XY", "210000")));
        assertEquals("Ccustomer_test2", customerRepository2.find(customer1.id()).name());
        assertEquals("Ccustomer_test2", customerRepository2.find2(customer1.id()).get(0).name());
        customerRepository.deleteAll();
    }
}
