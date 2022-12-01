package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class SaleRepositorySpec {

    @Inject ProductRepository productRepository;
    @Inject SaleRepository saleRepository;
    @Inject ManufacturerRepository manufacturerRepository;

    @Test
    void testReadWriteCustomType() {
        Manufacturer apple = manufacturerRepository.save("Apple");
        Product macBook = new Product("MacBook", apple);
        productRepository.save(macBook);

        Sale sale = saleRepository.save(new Sale(macBook, Quantity.valueOf(1)));

        assertNotNull(
            sale.getId()
        );
        assertEquals(1, sale.getQuantity().getAmount());

        sale = saleRepository.findById(sale.getId()).orElse(sale);
        assertNotNull(sale);
        assertEquals(1, sale.getQuantity().getAmount());
        assertTrue(saleRepository.findByQuantity(sale.getQuantity()).isPresent());
    }
}
