package example.h2;

import example.ManufacturerRepository;
import example.ProductRepository;
import example.SaleRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
class SaleRepositorySpec extends example.SaleRepositorySpec {
    public SaleRepositorySpec(ProductRepository productRepository, SaleRepository saleRepository, ManufacturerRepository manufacturerRepository) {
        super(productRepository, saleRepository, manufacturerRepository);
    }
}
