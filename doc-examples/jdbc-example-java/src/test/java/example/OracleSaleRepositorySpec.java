package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Requires(env="oracle")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "h2")
class OracleSaleRepositorySpec extends SaleRepositorySpec {
    public OracleSaleRepositorySpec(ProductRepository productRepository, SaleRepository saleRepository, ManufacturerRepository manufacturerRepository) {
        super(productRepository, saleRepository, manufacturerRepository);
    }
}
