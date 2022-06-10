package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class ProductRepositorySpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    @Shared
    ProductRepository productRepository

    @Inject
    @Shared
    ManufacturerRepository manufacturerRepository

    void setupSpec() {
        Manufacturer apple = manufacturerRepository.save("Apple").block()
        productRepository.saveAll(Arrays.asList(
                new Product(name: "MacBook",
                            manufacturer: apple),
                new Product(name: "iPhone",
                        manufacturer: apple)
        )).then().block()
    }

    void "test join spec"() {
        given:
        List<Product> list = productRepository.list().collectList().block()

        expect:
        list.every { p ->
            p.manufacturer.name == "Apple"
        }
    }

//    void "test find case-insensitive"() {
//        when:
//        long totalCaseInsensitive = productRepository.findByName("macbook", true, false).size();
//        long totalCaseSensitive = productRepository.findByName("macbook", false, false).size();
//
//        then:
//        totalCaseInsensitive == 1
//        totalCaseSensitive == 0
//    }
}
