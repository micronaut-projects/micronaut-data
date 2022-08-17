package example

import com.mongodb.client.model.Filters
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider
import io.micronaut.data.mongodb.transaction.MongoSynchronousTransactionManager
import io.micronaut.transaction.TransactionStatus
import jakarta.inject.Singleton

@Singleton
class ProductManager    // <1>
    (
    private val clientSession: com.mongodb.client.ClientSession,
    private val mongoDatabaseNameProvider: MongoDatabaseNameProvider,
    private val transactionManager: MongoSynchronousTransactionManager
) {
    fun save(name: String, manufacturer: Manufacturer): Product {
        return transactionManager.executeWrite {    // <2>
            val product = Product(name, manufacturer)
            transactionManager.client
                .getDatabase(mongoDatabaseNameProvider.provide(Product::class.java))
                .getCollection("product", Product::class.java)
                .insertOne(clientSession, product)
            product
        }
    }

    fun find(name: String): Product {
        return transactionManager.executeRead {
            transactionManager.client
                .getDatabase(mongoDatabaseNameProvider.provide(Product::class.java))
                .getCollection("product", Product::class.java)
                .find(clientSession, Filters.eq("name", name)).first()
        }
    }
}
