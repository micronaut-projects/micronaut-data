
package example

import com.mongodb.session.ClientSession
import io.micronaut.transaction.SynchronousTransactionManager
import jakarta.inject.Singleton
import org.bson.types.ObjectId

@Singleton
class ProductManager(
//        private val databaseFactory: MongoDatabaseFactory
//        private val clientSession: ClientSession,
        private val transactionManager: SynchronousTransactionManager<ClientSession>) // <1>
{

    fun save(name: String, manufacturer: Manufacturer): Product {
//        return transactionManager.executeWrite { // <2>
//            val product = Product(ObjectId(), name, manufacturer)
//            connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)").use { ps ->
//                ps.setString(1, name)
//                ps.setLong(2, manufacturer.id!!)
//                ps.execute()
//            }
//            product
//        }
        return Product(ObjectId(), name, manufacturer)
    }

    fun find(name: String): Product? {
//        return transactionManager.executeRead { // <3>
//            connection
//                    .prepareStatement("select * from product p where p.name = ?").use { ps ->
//                        ps.setString(1, name)
//                        ps.executeQuery().use { rs ->
//                            if (rs.next()) {
//                                return@executeRead Product(
//                                        rs.getLong("id"),
//                                        rs.getString("name"),
//                                        null)
//                            }
//                            return@executeRead null
//                        }
//                    }
//        }
        return null
    }
}
