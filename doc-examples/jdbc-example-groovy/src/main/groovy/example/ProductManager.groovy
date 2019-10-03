package example

import io.micronaut.transaction.SynchronousTransactionManager
import javax.inject.Singleton
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@Singleton
class ProductManager {

    private final Connection connection
    private final SynchronousTransactionManager<Connection> transactionManager

    ProductManager(
            Connection connection,
            SynchronousTransactionManager<Connection> transactionManager) { // <1>
        this.connection = connection
        this.transactionManager = transactionManager
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
        return transactionManager.executeRead{ // <3>
            connection
                .prepareStatement("select * from product p where p.name = ?").withCloseable {
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
}
