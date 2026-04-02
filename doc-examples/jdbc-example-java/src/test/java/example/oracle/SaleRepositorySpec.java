package example.oracle;

import example.ManufacturerRepository;
import example.ProductRepository;
import example.SaleRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Requires(env="oracle")
class SaleRepositorySpec extends example.SaleRepositorySpec {
    public SaleRepositorySpec(ProductRepository productRepository, SaleRepository saleRepository, ManufacturerRepository manufacturerRepository) {
        super(productRepository, saleRepository, manufacturerRepository);
    }
}
