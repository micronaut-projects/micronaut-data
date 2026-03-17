package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@MicronautTest
@Requires(env="h2")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "oracle")
class H2SaleRepositorySpec extends SaleRepositorySpec {
    public H2SaleRepositorySpec(ProductRepository productRepository, SaleRepository saleRepository, ManufacturerRepository manufacturerRepository) {
        super(productRepository, saleRepository, manufacturerRepository);
    }
}
