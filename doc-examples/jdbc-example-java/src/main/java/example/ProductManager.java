
package example;

import io.micronaut.transaction.SynchronousTransactionManager;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Singleton
public class ProductManager {

    private final Connection connection;
    private final SynchronousTransactionManager<Connection> transactionManager;

    public ProductManager(
            Connection connection,
            SynchronousTransactionManager<Connection> transactionManager) { // <1>
        this.connection = connection;
        this.transactionManager = transactionManager;
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite(status -> { // <2>
            final Product product = new Product(name, manufacturer);
            try (PreparedStatement ps =
                         connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)")) {
                ps.setString(1, name);
                ps.setLong(2, manufacturer.getId());
                ps.execute();
            }
            return product;
        });
    }

    Product find(String name) {
        return transactionManager.executeRead(status -> { // <3>
            try (PreparedStatement ps = connection
                    .prepareStatement("select * from product p where p.name = ?")) {
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
}
