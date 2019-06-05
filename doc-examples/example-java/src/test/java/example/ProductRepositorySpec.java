package example;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

@MicronautTest(transactional = false, rollback = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductRepositorySpec {

    @Inject ProductRepository productRepository;

    @BeforeAll
    void setupTest() {
        Manufacturer apple = productRepository.saveManufacturer("Apple");
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

    @Test
    void testJoinSpec() {
        List<Product> list = productRepository.list();
        Assertions.assertTrue(
                list.stream().allMatch( p ->
                    p.getManufacturer().getName().equals("Apple")
                )
        );
    }
}
