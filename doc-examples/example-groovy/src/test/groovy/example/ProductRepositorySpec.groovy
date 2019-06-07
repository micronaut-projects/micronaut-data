package example

import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import java.util.concurrent.TimeUnit

@MicronautTest(transactional = false, rollback = false)
class ProductRepositorySpec extends Specification {

    @Inject
    @Shared
    ProductRepository productRepository

    void setupSpec() {
        Manufacturer apple = productRepository.saveManufacturer("Apple")
        productRepository.saveAll(Arrays.asList(
                new Product(name: "MacBook",
                            manufacturer: apple),
                new Product(name: "iPhone",
                        manufacturer: apple)
        ))
    }

    void "test join spec"() {
        given:
        List<Product> list = productRepository.list()

        expect:
        list.every { p ->
            p.manufacturer.name == "Apple"
        }
    }

    void "test async"() throws Exception {
        // tag::async[]
        when:"A result is retrieved using async composition"
        long total = productRepository.findByNameContains("o")
                .thenCompose { product -> productRepository.countByManufacturerName(product.manufacturer.name) }
                .get(1000, TimeUnit.SECONDS)

        then:"the result is correct"
        total == 2
        // end::async[]
    }

    void "test reactive"(){
        // tag::reactive[]
        when:"A result is retrieved with reactive composition"
        long total = productRepository.queryByNameContains("o")
                .flatMap { product -> productRepository.countDistinctByManufacturerName(product.manufacturer.name).toMaybe() }
                .defaultIfEmpty(0L)
                .blockingGet()

        then:"The result is correct"
        total == 2
        // end::reactive[]
    }
}
