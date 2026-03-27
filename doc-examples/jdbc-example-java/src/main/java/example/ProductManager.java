
package example;

import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

@Singleton
public class ProductManager {
    private static final Logger LOG = LoggerFactory.getLogger(ProductManager.class);

    private final Connection connection;
    private final TransactionOperations<Connection> transactionManager;
    private final ProductRepository productRepository;

    public ProductManager(Connection connection,
                          TransactionOperations<Connection> transactionManager, // <1>
                          ProductRepository productRepository) {
        this.connection = connection;
        this.transactionManager = transactionManager;
        this.productRepository = productRepository;
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite(status -> { // <2>
            final Product product = new Product(name, manufacturer);
            try (PreparedStatement ps = connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)")) {
                ps.setString(1, name);
                ps.setLong(2, manufacturer.getId());
                ps.execute();
            }
            return product;
        });
    }

    Product find(String name) {
        return transactionManager.executeRead(status -> { // <3>
            try (PreparedStatement ps = status.getConnection().prepareStatement("select * from product p where p.name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Product(rs.getString("name"), null);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Creates new product using transaction operations and product repository.
     *
     * @param name the product name
     * @param manufacturer the manufacturer
     * @return the created product instance
     */
    Product saveUsingRepo(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite(status -> { // <4>
            return productRepository.save(new Product(name, manufacturer));
        });
    }

    /**
     * Finds product by name using transaction manager and product repository.
     *
     * @param name the product name
     * @return found product or null if none product found matching by name
     */
    Product findUsingRepo(String name) {
        return transactionManager.executeRead(status -> { // <5>
            return productRepository.findByName(name).orElse(null);
        });
    }

    /**
     * Creates new product using transaction operations and product repository.
     *
     * @param name the product name
     * @param manufacturer the manufacturer
     * @return the created product instance
     */
    Product saveUsingRepoAndMDC(String name, Manufacturer manufacturer) {
        UUID newUserId = UUID.randomUUID();
        MDC.put("userId", newUserId.toString());
        MDC.put("product", name);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new MdcPropagationContext()).propagate()) {
            return transactionManager.executeWrite(status -> {
                String product = MDC.get("product");
                LOG.info("Saving product {}", product);
                return productRepository.save(new Product(name, manufacturer));
            });
        }
    }

    /**
     * Creates new product using transaction operations and product repository.
     *
     * @param name the product name
     * @param manufacturer the manufacturer
     * @return the created product instance
     */
    Product saveUsingRepoAndMDCTransactional(String name, Manufacturer manufacturer) {
        UUID newUserId = UUID.randomUUID();
        MDC.put("userId", newUserId.toString());
        MDC.put("product", name);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new MdcPropagationContext()).propagate()) {
            Product product = saveProductTransactional(name, manufacturer);
            System.out.println("MDC.get(\"another\") = " + MDC.get("another"));
            return product;
        }
    }

    @Transactional
    Product saveProductTransactional(String name, Manufacturer manufacturer) {
        String product = MDC.get("product");
        LOG.info("Saving product {}", product);
        MDC.put("another", product);
        return productRepository.save(new Product(name, manufacturer));
    }

}
