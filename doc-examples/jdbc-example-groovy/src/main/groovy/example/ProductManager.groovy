package example

import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@Singleton
class ProductManager {

    private final Connection connection
    private final TransactionOperations<Connection> transactionManager
    private final ProductRepository productRepository

    ProductManager(Connection connection,
                   TransactionOperations<Connection> transactionManager, // <1>
                   ProductRepository productRepository) {
        this.connection = connection
        this.transactionManager = transactionManager
        this.productRepository = productRepository
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite { // <2>
            final Product product = new Product(name, manufacturer)
            connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)")
                    .withCloseable { PreparedStatement ps ->
                        ps.setString(1, name)
                        ps.setLong(2, manufacturer.getId())
                        ps.execute()
                    }
            return product
        }
    }

    Product find(String name) {
        return transactionManager.executeRead { status -> // <3>
            status.getConnection().prepareStatement("select * from product p where p.name = ?").withCloseable {
                PreparedStatement ps ->
                    ps.setString(1, name)
                    ps.executeQuery().withCloseable { ResultSet rs ->
                        if (rs.next()) {
                            return new Product(rs.getString("name"), null)
                        }
                        return null
                    }
            }
        }
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
        })
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
        })
    }
}
