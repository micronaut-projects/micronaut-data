
package example

import io.micronaut.transaction.SynchronousTransactionManager
import java.sql.Connection
import jakarta.inject.Singleton

@Singleton
class ProductManager(
        private val connection: Connection,
        private val transactionManager: SynchronousTransactionManager<Connection>) // <1>
{

    fun save(name: String, manufacturer: Manufacturer): Product {
        return transactionManager.executeWrite { // <2>
            val product = Product(0, name, manufacturer)
            connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)").use { ps ->
                ps.setString(1, name)
                ps.setLong(2, manufacturer.id!!)
                ps.execute()
            }
            product
        }
    }

    fun find(name: String): Product? {
        return transactionManager.executeRead { // <3>
            connection
                    .prepareStatement("select * from product p where p.name = ?").use { ps ->
                        ps.setString(1, name)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@executeRead Product(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        null)
                            }
                            return@executeRead null
                        }
                    }
        }
    }
}
