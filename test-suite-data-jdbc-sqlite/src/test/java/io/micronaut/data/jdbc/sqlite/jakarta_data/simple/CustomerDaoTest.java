package io.micronaut.data.jdbc.sqlite.jakarta_data.simple;

import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.data.Limit;
import jakarta.data.Sort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SQLiteDBProperties
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
