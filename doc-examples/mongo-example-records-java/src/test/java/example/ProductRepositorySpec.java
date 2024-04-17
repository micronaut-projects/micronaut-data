package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@MicronautTest(transactional = false, rollback = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductRepositorySpec {

    @Inject ProductRepository productRepository;
    @Inject ProductManager productManager;
    @Inject ManufacturerRepository manufacturerRepository;

    @BeforeAll
    void setupTest() {
        Manufacturer apple = manufacturerRepository.save("Apple");
        productRepository.saveAll(Arrays.asList(
                new Product(
                        "MacBook",
                        apple
                ),
                new Product(
                        "iPhone",
                        apple
                )
        ));
    }

    @AfterAll
    public void cleanup() {
        productRepository.deleteAll();
    }

    @Test
    void testJoinSpec() {
        List<Product> list = productRepository.list();
        Assertions.assertTrue(
                list.stream().allMatch( p ->
                    p.manufacturer().name().equals("Apple")
                )
        );
    }

    @Test
    void testAsync() throws Exception {
        // tag::async[]
        long total = productRepository.findByNameRegex(".*o.*")
                .thenCompose(product -> productRepository.countByManufacturerName(product.manufacturer().name()))
                .get(1000, TimeUnit.SECONDS);

        Assertions.assertEquals(
                2,
                total
        );
        // end::async[]
    }

    @Test
    void testReactive() {
        // tag::reactive[]
        long total = productRepository.queryByNameRegex(".*o.*")
                .flatMap(product -> productRepository.countDistinctByManufacturerName(product.manufacturer().name()))
                .defaultIfEmpty(0L)
                .block();

        Assertions.assertEquals(
                2,
                total
        );
        // end::reactive[]
    }

    @Test
    void testProgrammaticTransactions() {
        Manufacturer apple = manufacturerRepository.save("Apple");
        final Product watch = productManager.save("Watch", apple);

        Assertions.assertEquals(
                watch.name(),
                productManager.find("Watch").name()
        );
    }
}
