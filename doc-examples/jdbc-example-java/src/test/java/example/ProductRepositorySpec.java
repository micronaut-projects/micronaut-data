package example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class ProductRepositorySpec {

    @Inject ProductRepository productRepository;
    @Inject ProductManager productManager;
    @Inject ManufacturerRepository manufacturerRepository;

    @BeforeAll
    void setupTest() {
        if(productRepository != null) {
            productRepository.deleteAll();
        }
        if(manufacturerRepository != null) {
            manufacturerRepository.deleteAll();
            Manufacturer apple = manufacturerRepository.save("Apple");
            if(productRepository != null) {
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
        }
    }

    @Test
    void testNativeJoin() {
        List<Product> list = productRepository.searchProducts("MacBook%");
        Assertions.assertTrue(
                list.stream().allMatch( p ->
                        p.getManufacturer().getName().equals("Apple")
                )
        );
    }

    @Test
    void testJoinSpec() {
        List<Product> list = productRepository.list();
        Assertions.assertTrue(
                list.stream().allMatch( p ->
                    p.getManufacturer().getName().equals("Apple")
                )
        );
    }

    @Test
    void testAsync() throws Exception {
        // tag::async[]
        long total = productRepository.findByNameContains("o")
                .thenCompose(product -> productRepository.countByManufacturerName(product.getManufacturer().getName()))
                .get(1000, TimeUnit.SECONDS);

        Assertions.assertEquals(
                2,
                total
        );
        // end::async[]
    }

    @Test
    void testReactive() throws Exception {
        // tag::reactive[]
        long total = productRepository.queryByNameContains("o")
                .flatMap(product -> productRepository.countDistinctByManufacturerName(product.getManufacturer().getName())
                                        .toMaybe())
                .defaultIfEmpty(0L)
                .blockingGet();

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
                watch.getName(),
                productManager.find("Watch").getName()
        );
    }
}
