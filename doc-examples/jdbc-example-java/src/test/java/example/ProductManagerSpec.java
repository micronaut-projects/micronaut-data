package example;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class ProductManagerSpec {

    @Inject
    ProductManager productManager;

    @Inject
    ProductRepository productRepository;

    @Inject
    ManufacturerRepository manufacturerRepository;

    @BeforeAll
    void setupTest() {
        if(productRepository != null) {
            productRepository.deleteAll();
        }
        if(manufacturerRepository != null) {
            manufacturerRepository.deleteAll();
        }
    }

    @Test
    void testProductManager() {
        Manufacturer apple = manufacturerRepository.save("Apple");
        productManager.save("VR", apple);

        Product product = productManager.find("VR");
        assertEquals("VR", product.getName());
    }

    @Test
    void testProductManagerUsingRepo() {
        Manufacturer intel = manufacturerRepository.save("Intel");
        productManager.saveUsingRepo("Processor", intel);

        Product product = productManager.findUsingRepo("Processor");
        assertEquals("Processor", product.getName());

        product = productManager.findUsingRepo("NonExistingProduct");
        assertNull(product);
    }

}
