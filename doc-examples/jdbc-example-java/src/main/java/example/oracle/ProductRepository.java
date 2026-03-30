package example.oracle;

import example.Product;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
@Requires(env="oracle")
public interface ProductRepository extends example.ProductRepository {
    @Query("""
        SELECT p.*, m_.name as m_name, m_.id as m_id
        FROM product p
        INNER JOIN manufacturer m_ ON p.manufacturer_id = m_.id
        WHERE p.name like :name fetch first 5 rows only""")
    @Join(value = "manufacturer", alias = "m_")
    List<Product> searchProducts(String name);
}
