package example

import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

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
}
