package example;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static org.junit.jupiter.api.Assertions.*;
import javax.inject.Inject;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaleRepositorySpec {

    @Inject ProductRepository productRepository;
    @Inject SaleRepository saleRepository;

    @Test
    void testReadWriteCustomType() {
        Manufacturer apple = productRepository.saveManufacturer("Apple");
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
    }
}
