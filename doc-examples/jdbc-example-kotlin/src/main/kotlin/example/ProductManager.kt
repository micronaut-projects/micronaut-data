package example

import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import java.sql.Connection

@Singleton
class ProductManager(
    private val connection: Connection,
    private val transactionManager: TransactionOperations<Connection>, // <1>
    private val productRepository: ProductRepository
) {

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

    fun find(name: String): Product {
        return transactionManager.executeRead { status -> // <3>
            status.connection.prepareStatement("select * from product p where p.name = ?").use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            return@executeRead Product(
                                rs.getLong("id"), rs.getString("name"), null
                            )
                        }
                        throw EmptyResultException()
                    }
                }
        }
    }

    fun saveUsingRepo(name: String, manufacturer: Manufacturer): Product {
        return transactionManager.executeWrite { // <4>
            productRepository.save(Product(0, name, manufacturer))
        }
    }

    fun findUsingRepo(name: String): Product? {
        return transactionManager.executeRead { status -> // <5>
            productRepository.findByName(name).orElse(null)
        }
    }
}
