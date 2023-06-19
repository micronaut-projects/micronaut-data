package example

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider
import io.micronaut.data.mongodb.transaction.MongoTransactionOperations
import jakarta.inject.Singleton

@Singleton
class ProductManager    // <1>
    (
    private val clientSession: com.mongodb.client.ClientSession,
    private val mongoClient: MongoClient,
    private val mongoDatabaseNameProvider: MongoDatabaseNameProvider,
    private val mongoTransactionOperations: MongoTransactionOperations
) {
    fun save(name: String, manufacturer: Manufacturer): Product {
        return mongoTransactionOperations.executeWrite {    // <2>
            val product = Product(name, manufacturer)
            mongoClient
                .getDatabase(mongoDatabaseNameProvider.provide(Product::class.java))
                .getCollection("product", Product::class.java)
                .insertOne(clientSession, product)
            product
        }
    }

    fun find(name: String): Product {
        return mongoTransactionOperations.executeRead {
            mongoClient
                .getDatabase(mongoDatabaseNameProvider.provide(Product::class.java))
                .getCollection("product", Product::class.java)
                .find(clientSession, Filters.eq("name", name)).first()
        }
    }
}
