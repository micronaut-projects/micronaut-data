package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class SaleRepositorySpec {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final ManufacturerRepository manufacturerRepository;

    public SaleRepositorySpec(ProductRepository productRepository,
                              SaleRepository saleRepository,
                              ManufacturerRepository manufacturerRepository) {
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.manufacturerRepository = manufacturerRepository;
    }

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
