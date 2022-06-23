package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Arrays;
import java.util.List;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductRepositoryTest implements PostgresHibernateReactiveProperties {

    @Inject ProductRepository productRepository;
    @Inject ProductManager productManager;
    @Inject ManufacturerRepository manufacturerRepository;

    @BeforeAll
    void setupTest() {
        Manufacturer apple = manufacturerRepository.save("Apple").block();
        productRepository.saveAll(Arrays.asList(
                new Product(
                        "MacBook",
                        apple
                ),
                new Product(
                        "iPhone",
                        apple
                )
        )).then().block();
    }

    @Test
    void testJoinSpec() {
        List<Product> list = productRepository.list().collectList().block();
        Assertions.assertTrue(
                list.stream().allMatch( p ->
                    p.getManufacturer().getName().equals("Apple")
                )
        );
    }

    @Test
    void testProgrammaticTransactions() {
        Manufacturer apple = manufacturerRepository.save("Apple").block();
        final Product watch = productManager.save("Watch", apple).block();

        Assertions.assertEquals(
                watch.getName(),
                productManager.find("Watch").block().getName()
        );
    }

    @Test
    void testFindCaseInsensitive() {
        long totalCaseInsensitive = productRepository.findByName("macbook", true, false).collectList().block().size();
        long totalCaseSensitive = productRepository.findByName("macbook", false, false).collectList().block().size();

        Assertions.assertEquals(
                1,
                totalCaseInsensitive
        );
        Assertions.assertEquals(
                0,
                totalCaseSensitive
        );
    }
}
